'use strict';

var rcMod = angular.module('rcApp');

// Numbers : Incoming : List ---------------------------------------------------

rcMod.controller('NumbersCtrl', function ($scope, $resource, $uibModal, $dialog, $rootScope, $anchorScroll, SessionService, RCommNumbers, Notifications) {
  $anchorScroll(); // scroll to top
  $scope.sid = SessionService.get("sid");

  // edit incoming number friendly name --------------------------------------
  $scope.editingFriendlyName = "";

  $scope.setFriendlyName = function(pn) {
    var params = {PhoneNumber: pn.phone_number, FriendlyName: pn.friendly_name};

    RCommNumbers.update({accountSid:$scope.sid, phoneSid:pn.sid}, $.param(params),
      function() { // success
        $scope.editingFriendlyName = "";
      },
      function() { // error
        // TODO: Show alert
      }
    );
  };

  $scope.$watch('query.phone_number', function (phoneNumber) {
    if (phoneNumber !== undefined) {
      $scope.getNumbersList(0);
    }
  });

  // add incoming number -----------------------------------------------------
/*
// no modal is used for number registration any more
  $scope.showRegisterIncomingNumberModal = function () {
    var registerIncomingNumberModal = $uibModal.open({
      controller: NumberDetailsCtrl,
      scope: $scope,
      templateUrl: 'modules/modals/modal-register-incoming-number.html'
    });

    registerIncomingNumberModal.result.then(
      function () {
        // what to do on modal completion...
        $scope.numbersList = RCommNumbers.query({accountSid:$scope.sid});
      },
      function () {
        // what to do on modal dismiss...
      }
    );
  };
  */

  // delete incoming number --------------------------------------------------

  $scope.confirmNumberDelete = function(phone) {
    confirmNumberDelete(phone, $dialog, $scope, RCommNumbers, Notifications);
  };
  
//pagination support ----------------------------------------------------------------------------------------------

  $scope.currentPage = 1; //current page
  $scope.maxSize = 5; //pagination max size
  $scope.entryLimit = 10; //max rows for data table
  $scope.noOfPages = 1; //max rows for data table
  $scope.reverse = false;
  $scope.predicate = "phone_number";

  $scope.setEntryLimit = function(limit) {
    $scope.entryLimit = limit;
    $scope.currentPage = 1;
    $scope.getNumbersList($scope.currentPage-1);
  };

  $scope.pageChanged = function() {
    $scope.getNumbersList($scope.currentPage-1);
  };

  $scope.getNumbersList = function(page) {
    $scope.currentPage = (page || 0) + 1;
    var params = createSearchParams();
    RCommNumbers.get($.extend({accountSid: $scope.sid, Page: page, PageSize: $scope.entryLimit}, params), function(data) {
      $scope.numbersList = data.incomingPhoneNumbers;
      $scope.totalNumbers = data.total;
      $scope.noOfPages = data.num_pages;
      $scope.start = parseInt(data.start) + 1;
      $scope.end = parseInt(data.end);
      if ($scope.end !== $scope.totalNumbers) {
        ++$scope.end;
      }
    });
  };

  var createSearchParams = function() {
    var params = {};
    params["SortBy"] = $scope.predicate;
    params["Reverse"] = $scope.reverse;
    if ($scope.query && $scope.query.phone_number) {
      params['PhoneNumber'] = $scope.query.phone_number;
    }

    return params;
  };
 
  $scope.getNumbersList(0);

});

// Numbers : Incoming : Details (also used for Modal) --------------------------

rcMod.controller('NumberDetailsCtrl', function ($scope, $stateParams, $location, $dialog, $uibModalInstance, SessionService, RCommNumbers, RCommAvailableNumbers, Notifications, allCountries, providerCountries, localApps, $rootScope, AuthService, Applications) {

  // are we editing details...
  //if($scope.phoneSid === $stateParams.phoneSid) {

  $scope.sid = SessionService.get("sid");
  $scope.phoneSid = $stateParams.phoneSid

  $scope.numberDetails = RCommNumbers.get({accountSid:$scope.sid, phoneSid: $scope.phoneSid});

  $scope.localVoiceApps = Applications.filterByKind(localApps, 'voice');
  $scope.localSmsApps = Applications.filterByKind(localApps, 'sms');
  $scope.localUssdApps = Applications.filterByKind(localApps, 'ussd');

  //$scope.countries = countries;
  $scope.countries = allCountries;
  $scope.providerCountries = providerCountries;

  $scope.areaCodesUS = RCommAvailableNumbers.getAreaCodes({countryCode: 'US'});
  $scope.areaCodesCA = RCommAvailableNumbers.getAreaCodes({countryCode: 'CA'});
  $scope.selected = undefined;

  $scope.registerIncomingNumber = function(number) {
    var params = createNumberParams(number);
    RCommNumbers.register({accountSid: $scope.sid}, $.param(params),
      function() { // success
        Notifications.success('Number "' + number.phone_number + '" created successfully!');
        $uibModalInstance.close();
      },
      function() { // error
        Notifications.error('Failed to register number "' + number.phone_number + '".');
      }
    );
  };

  $scope.updateIncomingNumber = function(number) {
    var params = createNumberParams(number);
    RCommNumbers.update({accountSid: $scope.sid, phoneSid: $scope.phoneSid}, $.param(params),
      function() { // success
        Notifications.success('Number "' + number.phone_number + '" updated successfully!');
        $location.path( "/numbers/incoming" );
      },
      function() { // error
        Notifications.error('Failed to update number "' + number.phone_number + '".');
      }
    );
  };

  $scope.confirmNumberDelete = function(phone) {
    confirmNumberDelete(phone, $dialog, $scope, RCommNumbers, Notifications, $location);
  };

  $scope.searching = false;

  $scope.findNumbers = function(areaCode, countryCode) {
    $scope.searching = true;
    $scope.availableNumbers = null;
    if (countryCode === null || countryCode === "" || countryCode.length === 0 || countryCode.length === 1) {
      document.getElementById("countryCode").value = "US";
      countryCode = "US";
    }
    if(countryCode !== "US") {
      $scope.availableNumbers = RCommAvailableNumbers.query({accountSid: $scope.sid, countryCode: countryCode.code});
    } else {
      $scope.availableNumbers = RCommAvailableNumbers.query({accountSid: $scope.sid, countryCode: countryCode.code, areaCode: areaCode});
    }
    $scope.availableNumbers.$promise.then(
      //success
      function(value){
        $scope.searching = false;
      },
      //error
      function(error){
        $scope.searching = false;
      }
    );
  }
});

rcMod.controller('NumberRegisterCtrl', function ($scope, $stateParams, $location, $http, $dialog, SessionService, RCommNumbers, RCommAvailableNumbers, Notifications, allCountries, providerCountries) {

  $scope.sid = SessionService.get("sid");

  //$scope.countries = countries;
  $scope.countries = allCountries;
  $scope.providerCountries = providerCountries;

  $scope.areaCodesUS = RCommAvailableNumbers.getAreaCodes({countryCode: 'US'});
  $scope.areaCodesCA = RCommAvailableNumbers.getAreaCodes({countryCode: 'CA'});
  $scope.selected = undefined;

  $scope.setProvider = function(isProvider) {
    $scope.isProvider = isProvider;
  };

  $scope.registerIncomingNumber = function(number, isSIP) {
    confirmNumberRegister(number, isSIP, $dialog, $scope, RCommNumbers, Notifications, $location, $http);
  };

  $scope.configureNewNumber = function(number) {
  };

  $scope.searching = false;
  $scope.pageSize = 10;

  $scope.$watchGroup(['numberCaps', 'numberCapsVoice', 'numberCapsSms'], function() {
    var newNumberCaps = [];
    if ($scope.numberCaps === 'specific') {
      if ($scope.numberCapsVoice) {
        newNumberCaps.push('Voice');
      }
      if ($scope.numberCapsSms) {
        newNumberCaps.push('Sms');
      }
    }
    $scope.newNumber.capabilities = newNumberCaps;
  });

  $scope.findNumbers = function(pageNr) {
    $scope.searching = true;
    $scope.availableNumbers = null;
    var queryParams = {accountSid: $scope.sid, countryCode: $scope.newNumber.countryCode.code};
    if($scope.newNumber.area_code) { queryParams['AreaCode'] = $scope.newNumber.area_code; }
    if($scope.newNumber.phone_number) { queryParams['Contains'] = $scope.newNumber.phone_number; }
    angular.forEach($scope.newNumber.capabilities, function(value, key) {
      this[value + 'Enabled'] = 'true';
    }, queryParams);
    queryParams.RangeSize = $scope.pageSize || 10;
    queryParams.RangeIndex = $scope.currentPage = pageNr || 1;
    $scope.availableNumbers = RCommAvailableNumbers.query(queryParams);
    $scope.availableNumbers.$promise.then(
      //success
      function(value) {
        $scope.searching = false;
      },
      //error
      function(error) {
        $scope.searching = false;
      }
    );
  };

  $scope.nextRange = function() {
    $scope.findNumbers(++$scope.currentPage);
  };

  $scope.prevRange = function() {
    $scope.findNumbers(--$scope.currentPage);
  }

});


var confirmNumberDelete = function(phone, $dialog, $scope, RCommNumbers, Notifications, $location) {
  var title = 'Delete Number ' + phone.phone_number;
  var msg = 'Are you sure you want to delete incoming number ' + phone.phone_number + ' (' + phone.friendly_name +  ') ? This action cannot be undone.';
  var btns = [{result:'cancel', label: 'Cancel', cssClass: 'btn-default'}, {result:'confirm', label: 'Delete!', cssClass: 'btn-danger'}];

  $dialog.messageBox(title, msg, btns)
    .open()
    .then(function(result) {
      if (result === "confirm") {
        RCommNumbers.delete({accountSid:$scope.sid, phoneSid:phone.sid}, {},
          function() {
            Notifications.success('The incoming number "' + phone.phone_number + '" has been deleted.');
            if($location) {
              $location.path( "/numbers/incoming" );
            }
            else {
              $scope.numbersList = RCommNumbers.query({accountSid:$scope.sid});
            }
          },
          function() {
            // TODO: Show alert on delete failure...
            Notifications.error('Failed to delete the incoming number ' + phone.phone_number);
          }
        );
      }
    });
};

var confirmNumberRegister = function(phone, isSIP, $dialog, $scope, RCommNumbers, Notifications, $location, $http) {
  var newNumber = phone.phone_number || phone.phoneNumber;
  var newFriendly = phone.friendly_name || phone.friendlyName || newNumber;
  var newCost = phone.cost || 0;
  var title = 'Register Number ' + newNumber;
  var msg = 'Are you sure you want to register incoming number ' + newNumber + ' (' + newFriendly +  ') ? ' + ((isSIP || !newCost) ? '' : 'It will cost you ' + newCost + '.');
  var btns = [{result:'cancel', label: 'Cancel', cssClass: 'btn-default', id: 'register-number-' + (isSIP ? 'sip' : 'provider') + '-cancel'}, {result:'confirm', label: 'Register', cssClass: 'btn-primary', id: 'register-number-' + (isSIP ? 'sip' : 'provider') + '-confirm'}];

  $dialog.messageBox(title, msg, btns)
    .open()
    .then(function(result) {
      if (result === "confirm") {
        var params = createNumberParams(phone, isSIP);
        RCommNumbers.register({accountSid: $scope.sid}, $.param(params),
         function(phone, headers) { // success
           phone.registered = true;
           // TODO: add assigned id to number so it can be used with configure
           Notifications.success('Number "' + (phone.phone_number || phone.phoneNumber) + '" created successfully!');
           $location.path('/numbers/incoming/' + phone.sid);
         },
         function(httpResponse) { // error
           Notifications.error('Failed to register number "' + (phone.phone_number || phone.phoneNumber) + '".');
         }
       );
        /*
        // FIXME: for some reason $resource error callback is not called on error..
        $http({
          method: 'POST',
          url: '/restcomm/2012-04-24/Accounts/' + $scope.sid + '/IncomingPhoneNumbers.json',
          data: $.param(params),
          headers: {'Content-Type': 'application/x-www-form-urlencoded'}
        }).
          success(
          function(a, b) { // success
            console.log(a);
            console.log(b);
            phone.registered = true;
            // TODO: add assigned id to number so it can be used with configure
            Notifications.success('Number "' + (phone.phone_number || phone.phoneNumber) + '" created successfully!');
          }).error(
          function(e) { // error
            console.log(e);
            Notifications.error('Failed to register number "' + (phone.phone_number || phone.phoneNumber) + '".');
          }
        )*/
      }
    });
};

var createNumberParams = function(number) {
  createNumberParams(number, false)
};

var createNumberParams = function(number, isSIP) {
  var params = {};

  // Mandatory fields
  params["PhoneNumber"] = number.phone_number || number.phoneNumber;
  params["AreaCode"] = number.area_code || number.areaCode;

  if (!params["PhoneNumber"] && !params["AreaCode"]) {
    alert("You must provide either Number or Area Code.");
    return params;
  }

  // Optional fields
  params["FriendlyName"] = number.friendly_name || number.friendlyName;
  params["VoiceApplicationSid"] = number.voice_application_sid; // || number.voiceApplicationSid;
  params["VoiceUrl"] = number.voice_url; // || number.voiceUrl; - return "" as "". It will help the server clear values.
  params["VoiceMethod"] = number.voice_method || number.voiceMethod;
  params["VoiceFallbackUrl"] = number.voice_fallback_url; // || number.voiceFallbackUrl;
  params["VoiceFallbackMethod"] = number.voice_fallback_method || number.voiceFallbackMethod;
  params["StatusCallback"] = number.status_callback; // || number.statusCallback;
  params["StatusCallbackMethod"] = number.status_callback_method || number.statusCallbackMethod;
  params["SmsApplicationSid"] = number.sms_application_sid; // || number.smsApplicationSid;
  params["SmsUrl"] = number.sms_url; // || number.smsUrl;
  params["SmsMethod"] = number.sms_method || number.smsMethod;
  params["SmsFallbackUrl"] = number.sms_fallback_url; // || number.smsFallbackUrl;
  params["SmsFallbackMethod"] = number.sms_fallback_method || number.smsFallbackMethod;
  params["VoiceCallerIdLookup"] = number.voice_caller_id_lookup || number.voiceCallerIdLookup;
  params["UssdUrl"] = number.ussd_url;
  params["UssdMethod"] = number.ussd_method;
  params["UssdFallbackUrl"] = number.ussd_fallback_url;
  params["UssdFallbackMethod"] = number.ussd_fallback_method;
  params["UssdApplicationSid"] = number.ussd_application_sid;
  params["ReferUrl"] = number.refer_url;
  params["ReferMethod"] = number.refer_method;
  params["ReferApplicationSid"] = number.refer_application_sid; // || number.referApplicationSid;

  if(isSIP) {
    params["isSIP"] = "true";
  }

  for (var prop in params) {
    if (params.hasOwnProperty(prop) && params[prop] === undefined) {
      delete params[prop];
    }
  }

  return params;
};
