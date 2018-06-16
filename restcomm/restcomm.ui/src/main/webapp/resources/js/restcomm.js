var rcMod = angular.module('rcApp', [
  'rcApp.filters',
  'rcApp.services',
  'rcApp.directives',
  'rcApp.controllers',
  'ngResource',
  'ui.bootstrap',
  'angular-md5',
  'ui.bootstrap.modal.dialog',
  'ngFileUpload',
  'ngPasswordStrength',
  'nvd3',
  'ngSanitize',
  'ui.router',
  'ui.carousel',
  'ngFileSaver'
]);

rcMod.config(['$locationProvider', function($locationProvider) {
  $locationProvider.hashPrefix('');
}]);

// For all states that that have resolve sections that rely on a determined authorization status (AuthService.checkAccess()) and are children of 'restcomm' state, the 'authorize' value should be injected in the dependent 'resolve' values. See state 'restcomm.incoming-phone / localApps'.
rcMod.config(['$stateProvider','$urlRouterProvider', function($stateProvider, $urlRouterProvider) {
  $stateProvider.state('public',{
    templateUrl:'templates/public-state.html'
  });
  $stateProvider.state('public.login',{
    url:"/login",
    templateUrl:'modules/login.html',
    controller:'LoginCtrl'
  });
  $stateProvider.state('public.uninitialized',{
    url:"/uninitialized",
    templateUrl: 'modules/uninitialized.html',
    controller:'UninitializedCtrl',
    resolve: {
        uninitialized: function (AuthService,$q) {
            try {
                return $q.when(AuthService.checkAccess()).then(function () {
                    throw 'ACCOUNT_ALREADY_INITIALIZED';
                });
            } catch (err) {
                if (err == 'RESTCOMM_ACCOUNT_NOT_INITIALIZED')
                    return; // ok, you're in the right place
                else
                    throw err; // raise again
            }
        }
    }
  });
  // 'restcomm' state assumes (requires) an authorized restcomm Account to have been determined. Child states can take that for granted.
  $stateProvider.state('restcomm',{
    templateUrl:'templates/restcomm-state.html',
    controller:'RestcommCtrl',
    resolve: {
        authorize: function (AuthService) {
            return AuthService.checkAccess();
        }
    }
  });
  $stateProvider.state('restcomm.home',{
    url:'/home',
    templateUrl:'modules/home.html',
    controller: 'DashboardCtrl'
  });
  $stateProvider.state('restcomm.dashboard',{
    url:'/dashboard',
    templateUrl:'modules/dashboard.html',
    controller: 'DashboardCtrl'
  });
  $urlRouterProvider.when('/numbers','/numbers/incoming'); //redirect to numbers/incoming
  $stateProvider.state('restcomm.numbers-incoming',{
    url:'/numbers/incoming',
    templateUrl:'modules/numbers-incoming.html',
    controller:'NumbersCtrl'
  });
  $stateProvider.state('restcomm.profile',{
    url:'/profile',
    templateUrl:'modules/profile.html',
    controller:'ProfileCtrl'
  });
  $stateProvider.state('restcomm.profile-account',{
    url:'/profile/:accountSid',
    templateUrl:'modules/profile.html',
    controller:'ProfileCtrl'
  });
  $stateProvider.state('restcomm.subaccounts',{
    url:'/subaccounts',
    templateUrl:'modules/subaccounts.html',
    controller:'SubAccountsCtrl',
    resolve: {
      subAccountsList: function(RCommAccounts) {
        return RCommAccounts.query().$promise;
      }
    }
  });
  $stateProvider.state('restcomm.register-incoming',{
    url:'/numbers/register-incoming',
    templateUrl:'modules/numbers-incoming-register.html',
    controller:'NumberRegisterCtrl',
    resolve: {
        $uibModalInstance : function() { return undefined; },
        allCountries : function(RCommAvailableNumbers) { return RCommAvailableNumbers.getCountries().$promise; },
        providerCountries: function(RCommAvailableNumbers, AuthService, authorize) { return RCommAvailableNumbers.getAvailableCountries({accountSid:AuthService.getAccountSid()}).$promise; }
    }
  });
  $stateProvider.state('restcomm.incoming-phone',{
    url:'/numbers/incoming/:phoneSid',
    templateUrl:'modules/numbers-incoming-details.html',
    controller:'NumberDetailsCtrl',
    resolve: {
        $uibModalInstance : function() {return undefined;},
        allCountries : function() {return undefined;},
        providerCountries : function() {return undefined;},
        localApps: function (RCommApplications, AuthService, authorize) { return RCommApplications.query({accountSid:AuthService.getAccountSid()}).$promise;}
    }
  });
  $stateProvider.state('restcomm.applications',{
    url:'/applications',
    templateUrl: 'modules/applications.html',
    controller: 'ApplicationsCtrl'
  });
  $stateProvider.state('restcomm.application-creation-wizard',{
      url:'/applications/creation-wizard',
      templateUrl: 'modules/application-creation-wizard.html',
      controller: 'ApplicationCreationWizardCtrl'
  });
  $stateProvider.state('restcomm.application-creation',{
      url:'/applications/new',
      templateUrl: 'modules/application-creation.html',
      controller: 'ApplicationCreationCtrl',
  });
  $stateProvider.state('restcomm.application-creation-from-template',{
      url:'/applications/new/:templateId',
      templateUrl: 'modules/application-creation.html',
      controller: 'ApplicationCreationCtrl',
  });

  $stateProvider.state('restcomm.application-external-creation',{
      url:'/applications/new-external',
      templateUrl: 'modules/application-creation.html',
      controller: 'ApplicationExternalCreationCtrl'
  });
  $stateProvider.state('restcomm.application-details',{
      url:'/applications/:applicationSid?firstTime',
      templateUrl: 'modules/application-details.html',
      controller: 'ApplicationDetailsCtrl'
  });
  $stateProvider.state('restcomm.application-templates',{
        url:'/application-templates',
        templateUrl: 'modules/application-templates.html',
        controller: 'ApplicationTemplatesCtrl'
  });
  $stateProvider.state('restcomm.clients',{
    url:'/numbers/clients',
    templateUrl: 'modules/numbers-clients.html',
    controller: 'ClientsCtrl'
  });
  $stateProvider.state('restcomm.client-details', {
    url:'/numbers/clients/:clientSid',
    templateUrl: 'modules/numbers-clients-details.html',
    controller: 'ClientDetailsCtrl',
    resolve: {
        $uibModalInstance : function() {return undefined;},
        localApps: function (RCommApplications, AuthService, authorize) { return RCommApplications.query({accountSid:AuthService.getAccountSid()}).$promise;}
    }
  });
  $urlRouterProvider.when('/logs','/logs/calls');  // redirect /logs to /logs/calls
  $stateProvider.state('restcomm.logs-calls',{
    url:'/logs/calls',
    templateUrl: 'modules/logs-calls.html',
    controller: 'LogsCallsCtrl'
  });
  $stateProvider.state('restcomm.logs-call-details', {
    url:'/logs/calls/:callSid',
    templateUrl: 'modules/logs-calls-details.html',
    controller: 'LogsCallsDetailsCtrl',
    resolve: {
        $uibModalInstance : function() {return undefined;},
        callSid: function() {}
    }
  });
  $stateProvider.state('restcomm.logs-messages', {
    url:'/logs/messages',
    templateUrl: 'modules/logs-messages.html',
    controller: 'LogsMessagesCtrl'
  });
  $stateProvider.state('restcomm.logs-recordings',{
    url:'/logs/recordings',
    templateUrl: 'modules/logs-recordings.html',
    controller: 'LogsRecordingsCtrl'
  });
  $stateProvider.state('restcomm.logs-transcriptions',{
    url:'/logs/transcriptions',
    templateUrl: 'modules/logs-transcriptions.html',
    controller: 'LogsTranscriptionsCtrl'
  });
  $stateProvider.state('restcomm.logs-notifications',{
    url:'/logs/notifications',
    templateUrl: 'modules/logs-notifications.html',
    controller: 'LogsNotificationsCtrl'
  });
  $urlRouterProvider.otherwise('/home');

}]);

angular.element(document).ready(['$http',function ($http) {
  // manually inject $q since it's not available
  var initInjector = angular.injector(['ng']);
  var $q = initInjector.get('$q');
  var $window = initInjector.get('$window');

  var configPromise = $q.defer();
  $http.get($window.location.pathname + 'conf/dashboard.json').success(function (response) {
    angular.module('rcApp.services').factory('PublicConfig', function () {
      return typeof response === 'string' ? JSON.parse(response) : response;
    });
    configPromise.resolve(response.data);
  }).error(function () {
    configPromise.reject();
  });

  $q.all([configPromise.promise]).then(function (responses) {
    angular.bootstrap(document, ['rcApp']);
  }, function (error) {
    console.error('Internal server error', error);
  });
}]);


rcMod.directive('equals', function() {
  return {
    restrict: 'A', // only activate on element attribute
    require: '?ngModel', // get a hold of NgModelController
    link: function(scope, elem, attrs, ngModel) {
      if(!ngModel) return; // do nothing if no ng-model

      // watch own value and re-validate on change
      scope.$watch(attrs.ngModel, function() {
        validate();
      });

      // observe the other value and re-validate on change
      attrs.$observe('equals', function (val) {
        validate();
      });

      var validate = function() {
        // values
        var val1 = ngModel.$viewValue;
        var val2 = attrs.equals;

        // set validity
        ngModel.$setValidity('equals', val1 === val2);
      };
    }
  }
});

rcMod.run(function($rootScope, $location, $anchorScroll, AuthService) {

  $rootScope.fac = {
    'SubAccounts': {
      title: 'Feature not available',
      message: 'The Sub Accounts feature is not available in your account.',
      condition: '!!!accountProfile.featureEnablement.subaccountsCreation',
      wrapper: true,
      placement: 'right',
      styles: {'width': 'fit-content'}
    },
    'SubAccountsLimit': {
      title: 'Limit reached',
      message: 'The maximum number of Sub Accounts for your account has been reached.',
      condition: '!!!accountProfile.featureEnablement.subaccountsCreation || accountProfile.featureEnablement.subaccountsCreation.limit <= subAccountsList.length',
      wrapper: true,
      placement: 'left',
      styles: {'pointer-events': 'all !important', 'display': 'block', 'float': 'right'}
    },
    'DID': {
      title: 'Feature not available',
      message: 'The provider number purchasing feature is not available in your account.',
      condition: '!!!accountProfile.featureEnablement.DIDPurchase',
      wrapper: true,
      placement: 'right',
      styles: {'width': 'fit-content'}
    },
    'DIDCountry': {
      title: 'Country not avaialble',
      message: 'The selected country is not available for purchasing Numbers in your account.',
      condition: 'newNumber.countryCode.code && accountProfile.featureEnablement.DIDPurchase.allowedCountries.indexOf(newNumber.countryCode.code) < 0',
      open: true,
      placement: 'bottom'
    }
  };

  $rootScope.$on("$routeChangeStart", function(event, next, current) {
    $anchorScroll(); // scroll to top
    //if(!AuthService.isLoggedIn()) {
    //  $location.path("/login");
    //}
  })
});

// There is a circular dependency issue when directly injecting AuthService in the function. A workaround using $injector has
// been used - http://stackoverflow.com/questions/20647483/angularjs-injecting-service-into-a-http-interceptor-circular-dependency
rcMod.
  factory('authHttpResponseInterceptor',['$q','$location','$injector','Notifications', 'PublicConfig',function($q,$location,$injector, Notifications, PublicConfig){
    return {
      request: function(config) {
          var restcomm_prefix = "/restcomm/";
          var rvd_prefix = PublicConfig.rvdUrl + '/';
          if ( ! config.headers.Authorization ) { // if no header is already present
              if ( config.url.substring(0, rvd_prefix.length) === rvd_prefix || config.url.substring(0, restcomm_prefix.length) === restcomm_prefix  ) {
                  var AuthService = $injector.get('AuthService');
                  var account = AuthService.getAccount();
                  if (!!account) {
                      var auth_header = account.email_address + ":" + account.auth_token;
                      auth_header = "Basic " + btoa(auth_header);
                      config.headers.Authorization = auth_header;
                  }
              }
          }
          return config;
      },
      response: function(response){
            var AuthService = $injector.get('AuthService');
            if (response.status === 401) {
              AuthService.onAuthError();
            } else
            if (response.status === 403) {
              AuthService.onError403();
            }
            return response || $q.when(response);
      },
      responseError: function(rejection) {
            var AuthService = $injector.get('AuthService');
            if (rejection.status === 401) {
              AuthService.onAuthError();
            } else
            if (rejection.status === 403) {
              AuthService.onError403();
            }
            return $q.reject(rejection);
      }
    }
  }])
  .config(['$httpProvider', function($httpProvider) {
      // http Intercpetor to check auth failures for xhr requests
      $httpProvider.interceptors.push('authHttpResponseInterceptor');
  }]);

/*
var interceptor = ['$rootScope', '$q', '$location', function (scope, $q, $location) {

  function success(response) {
    return response;
  }

  function error(response) {
    var status = response.status;

    if (status == 401) {
      console.log("Redirecting to login due to 401 ERROR CODE (@" + $location.url() + ")");
      $location.path("/login");
      return $q.reject(response);
    }
    // otherwise
    return $q.reject(response);

  }

  return function (promise) {
    return promise.then(success, error);
  }

}];
*/

// MD5
angular.module('angular-md5', []).factory('md5', [function() {
    var md5 = {
      createHash: function(str) {
        var xl;

        var rotateLeft = function (lValue, iShiftBits) {
          return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
        };

        var addUnsigned = function (lX, lY) {
          var lX4, lY4, lX8, lY8, lResult;
          lX8 = (lX & 0x80000000);
          lY8 = (lY & 0x80000000);
          lX4 = (lX & 0x40000000);
          lY4 = (lY & 0x40000000);
          lResult = (lX & 0x3FFFFFFF) + (lY & 0x3FFFFFFF);
          if (lX4 & lY4) {
            return (lResult ^ 0x80000000 ^ lX8 ^ lY8);
          }
          if (lX4 | lY4) {
            if (lResult & 0x40000000) {
              return (lResult ^ 0xC0000000 ^ lX8 ^ lY8);
            } else {
              return (lResult ^ 0x40000000 ^ lX8 ^ lY8);
            }
          } else {
            return (lResult ^ lX8 ^ lY8);
          }
        };

        var _F = function (x, y, z) {
          return (x & y) | ((~x) & z);
        };
        var _G = function (x, y, z) {
          return (x & z) | (y & (~z));
        };
        var _H = function (x, y, z) {
          return (x ^ y ^ z);
        };
        var _I = function (x, y, z) {
          return (y ^ (x | (~z)));
        };

        var _FF = function (a, b, c, d, x, s, ac) {
          a = addUnsigned(a, addUnsigned(addUnsigned(_F(b, c, d), x), ac));
          return addUnsigned(rotateLeft(a, s), b);
        };

        var _GG = function (a, b, c, d, x, s, ac) {
          a = addUnsigned(a, addUnsigned(addUnsigned(_G(b, c, d), x), ac));
          return addUnsigned(rotateLeft(a, s), b);
        };

        var _HH = function (a, b, c, d, x, s, ac) {
          a = addUnsigned(a, addUnsigned(addUnsigned(_H(b, c, d), x), ac));
          return addUnsigned(rotateLeft(a, s), b);
        };

        var _II = function (a, b, c, d, x, s, ac) {
          a = addUnsigned(a, addUnsigned(addUnsigned(_I(b, c, d), x), ac));
          return addUnsigned(rotateLeft(a, s), b);
        };

        var convertToWordArray = function (str) {
          var lWordCount;
          var lMessageLength = str.length;
          var lNumberOfWords_temp1 = lMessageLength + 8;
          var lNumberOfWords_temp2 = (lNumberOfWords_temp1 - (lNumberOfWords_temp1 % 64)) / 64;
          var lNumberOfWords = (lNumberOfWords_temp2 + 1) * 16;
          var lWordArray = new Array(lNumberOfWords - 1);
          var lBytePosition = 0;
          var lByteCount = 0;
          while (lByteCount < lMessageLength) {
            lWordCount = (lByteCount - (lByteCount % 4)) / 4;
            lBytePosition = (lByteCount % 4) * 8;
            lWordArray[lWordCount] = (lWordArray[lWordCount] | (str.charCodeAt(lByteCount) << lBytePosition));
            lByteCount++;
          }
          lWordCount = (lByteCount - (lByteCount % 4)) / 4;
          lBytePosition = (lByteCount % 4) * 8;
          lWordArray[lWordCount] = lWordArray[lWordCount] | (0x80 << lBytePosition);
          lWordArray[lNumberOfWords - 2] = lMessageLength << 3;
          lWordArray[lNumberOfWords - 1] = lMessageLength >>> 29;
          return lWordArray;
        };

        var wordToHex = function (lValue) {
          var wordToHexValue = "",
            wordToHexValue_temp = "",
            lByte, lCount;
          for (lCount = 0; lCount <= 3; lCount++) {
            lByte = (lValue >>> (lCount * 8)) & 255;
            wordToHexValue_temp = "0" + lByte.toString(16);
            wordToHexValue = wordToHexValue + wordToHexValue_temp.substr(wordToHexValue_temp.length - 2, 2);
          }
          return wordToHexValue;
        };

        var x = [],
          k, AA, BB, CC, DD, a, b, c, d, S11 = 7,
          S12 = 12,
          S13 = 17,
          S14 = 22,
          S21 = 5,
          S22 = 9,
          S23 = 14,
          S24 = 20,
          S31 = 4,
          S32 = 11,
          S33 = 16,
          S34 = 23,
          S41 = 6,
          S42 = 10,
          S43 = 15,
          S44 = 21;

        //str = this.utf8_encode(str);
        x = convertToWordArray(str);
        a = 0x67452301;
        b = 0xEFCDAB89;
        c = 0x98BADCFE;
        d = 0x10325476;

        xl = x.length;
        for (k = 0; k < xl; k += 16) {
          AA = a;
          BB = b;
          CC = c;
          DD = d;
          a = _FF(a, b, c, d, x[k + 0], S11, 0xD76AA478);
          d = _FF(d, a, b, c, x[k + 1], S12, 0xE8C7B756);
          c = _FF(c, d, a, b, x[k + 2], S13, 0x242070DB);
          b = _FF(b, c, d, a, x[k + 3], S14, 0xC1BDCEEE);
          a = _FF(a, b, c, d, x[k + 4], S11, 0xF57C0FAF);
          d = _FF(d, a, b, c, x[k + 5], S12, 0x4787C62A);
          c = _FF(c, d, a, b, x[k + 6], S13, 0xA8304613);
          b = _FF(b, c, d, a, x[k + 7], S14, 0xFD469501);
          a = _FF(a, b, c, d, x[k + 8], S11, 0x698098D8);
          d = _FF(d, a, b, c, x[k + 9], S12, 0x8B44F7AF);
          c = _FF(c, d, a, b, x[k + 10], S13, 0xFFFF5BB1);
          b = _FF(b, c, d, a, x[k + 11], S14, 0x895CD7BE);
          a = _FF(a, b, c, d, x[k + 12], S11, 0x6B901122);
          d = _FF(d, a, b, c, x[k + 13], S12, 0xFD987193);
          c = _FF(c, d, a, b, x[k + 14], S13, 0xA679438E);
          b = _FF(b, c, d, a, x[k + 15], S14, 0x49B40821);
          a = _GG(a, b, c, d, x[k + 1], S21, 0xF61E2562);
          d = _GG(d, a, b, c, x[k + 6], S22, 0xC040B340);
          c = _GG(c, d, a, b, x[k + 11], S23, 0x265E5A51);
          b = _GG(b, c, d, a, x[k + 0], S24, 0xE9B6C7AA);
          a = _GG(a, b, c, d, x[k + 5], S21, 0xD62F105D);
          d = _GG(d, a, b, c, x[k + 10], S22, 0x2441453);
          c = _GG(c, d, a, b, x[k + 15], S23, 0xD8A1E681);
          b = _GG(b, c, d, a, x[k + 4], S24, 0xE7D3FBC8);
          a = _GG(a, b, c, d, x[k + 9], S21, 0x21E1CDE6);
          d = _GG(d, a, b, c, x[k + 14], S22, 0xC33707D6);
          c = _GG(c, d, a, b, x[k + 3], S23, 0xF4D50D87);
          b = _GG(b, c, d, a, x[k + 8], S24, 0x455A14ED);
          a = _GG(a, b, c, d, x[k + 13], S21, 0xA9E3E905);
          d = _GG(d, a, b, c, x[k + 2], S22, 0xFCEFA3F8);
          c = _GG(c, d, a, b, x[k + 7], S23, 0x676F02D9);
          b = _GG(b, c, d, a, x[k + 12], S24, 0x8D2A4C8A);
          a = _HH(a, b, c, d, x[k + 5], S31, 0xFFFA3942);
          d = _HH(d, a, b, c, x[k + 8], S32, 0x8771F681);
          c = _HH(c, d, a, b, x[k + 11], S33, 0x6D9D6122);
          b = _HH(b, c, d, a, x[k + 14], S34, 0xFDE5380C);
          a = _HH(a, b, c, d, x[k + 1], S31, 0xA4BEEA44);
          d = _HH(d, a, b, c, x[k + 4], S32, 0x4BDECFA9);
          c = _HH(c, d, a, b, x[k + 7], S33, 0xF6BB4B60);
          b = _HH(b, c, d, a, x[k + 10], S34, 0xBEBFBC70);
          a = _HH(a, b, c, d, x[k + 13], S31, 0x289B7EC6);
          d = _HH(d, a, b, c, x[k + 0], S32, 0xEAA127FA);
          c = _HH(c, d, a, b, x[k + 3], S33, 0xD4EF3085);
          b = _HH(b, c, d, a, x[k + 6], S34, 0x4881D05);
          a = _HH(a, b, c, d, x[k + 9], S31, 0xD9D4D039);
          d = _HH(d, a, b, c, x[k + 12], S32, 0xE6DB99E5);
          c = _HH(c, d, a, b, x[k + 15], S33, 0x1FA27CF8);
          b = _HH(b, c, d, a, x[k + 2], S34, 0xC4AC5665);
          a = _II(a, b, c, d, x[k + 0], S41, 0xF4292244);
          d = _II(d, a, b, c, x[k + 7], S42, 0x432AFF97);
          c = _II(c, d, a, b, x[k + 14], S43, 0xAB9423A7);
          b = _II(b, c, d, a, x[k + 5], S44, 0xFC93A039);
          a = _II(a, b, c, d, x[k + 12], S41, 0x655B59C3);
          d = _II(d, a, b, c, x[k + 3], S42, 0x8F0CCC92);
          c = _II(c, d, a, b, x[k + 10], S43, 0xFFEFF47D);
          b = _II(b, c, d, a, x[k + 1], S44, 0x85845DD1);
          a = _II(a, b, c, d, x[k + 8], S41, 0x6FA87E4F);
          d = _II(d, a, b, c, x[k + 15], S42, 0xFE2CE6E0);
          c = _II(c, d, a, b, x[k + 6], S43, 0xA3014314);
          b = _II(b, c, d, a, x[k + 13], S44, 0x4E0811A1);
          a = _II(a, b, c, d, x[k + 4], S41, 0xF7537E82);
          d = _II(d, a, b, c, x[k + 11], S42, 0xBD3AF235);
          c = _II(c, d, a, b, x[k + 2], S43, 0x2AD7D2BB);
          b = _II(b, c, d, a, x[k + 9], S44, 0xEB86D391);
          a = addUnsigned(a, AA);
          b = addUnsigned(b, BB);
          c = addUnsigned(c, CC);
          d = addUnsigned(d, DD);
        }

        var temp = wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d);

        return temp.toLowerCase();
      }

    };

    return md5;

  }])
  .filter('gravatar', ['md5', function(md5) {
    return function(text) {
      return (text) ? md5.createHash(text.toLowerCase()) : '';
    };
  }])
  .filter('availableCountries', function() {
    return function(countries, avail) {
      return countries.filter(function(country) {
        for(a in avail) {
          if((""+avail[a][0]+avail[a][1]) === country.code) {
            return true;
          }
        }
        return false;
/*
          if (avail.indexOf(country.code) !== -1) {
            return true;
          }
        return false;
*/

      });
    };
  });
