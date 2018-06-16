'use strict';

/* Directives */
var rcDirectives = angular.module('rcApp.directives', []);

rcDirectives.directive('passwordMatch', [function () {
  return {
    restrict: 'A',
    scope:true,
    require: 'ngModel',
    link: function (scope, elem , attrs,control) {
      var checker = function () {

        //get the value of the first password
        var e1 = scope.$eval(attrs.ngModel);

        //get the value of the other password 
        var e2 = scope.$eval(attrs.passwordMatch);
        return e1 == e2;
      };
      scope.$watch(checker, function (n) {

        //set the form control to valid if both
        //passwords are the same, else invalid
        control.$setValidity("unique", n);
      });
    }
  };
}]);

rcDirectives.directive('autofocus', function ($timeout) {
  return {
    restrict: 'A',
    link: function (scope, element, attrs) {
      scope.$watch(attrs.autofocus, function (newValue) {
        $timeout(function () {
          if (attrs.autofocus === '' || newValue) {
            element[0].focus();
            if (element[0].select) {
              element[0].select();
            }
          }
        }, 25);
      });
    }
  };
});

rcDirectives.directive('rcFac', function ($interpolate, $compile, $parse) {
  return {
    link: function(scope, element, attrs) {
      var facObject = scope.$eval(attrs.rcFac);

      var facCondition = (facObject && facObject.condition) || attrs.facCondition;
      var facTitle = attrs.facTitle || facObject.title;
      var facMessage = attrs.facMessage || facObject.message;
      var facAutoOpen = !!facObject.open;
      var facWrapper = !!facObject.wrapper;
      var facPlacement = facObject.placement || 'auto';
      var facStyles = facObject.styles;

      var disabled = $parse(facCondition)(scope);
      element.prop('disabled', disabled);

      if ((disabled || facAutoOpen) && !attrs.title) {
        element.prop('title', facTitle);
        element.css({'pointer-events': 'none'});

        var wrapper = facWrapper ? element.wrap('<div></div>').parent() : element;

        if (facStyles) {
          wrapper.css(facStyles);
        }
        wrapper.attr('popover-title', facTitle);
        wrapper.attr('uib-popover-html', '\'' + facMessage + '\'');
        wrapper.attr('popover-placement', facPlacement);
        wrapper.attr('popover-append-to-body', 'true');

        if (facAutoOpen) {
          wrapper.attr('popover-is-open', facCondition);
        }
        else {
          wrapper.attr('popover-trigger', '"mouseenter"');
          wrapper.css({'cursor': 'not-allowed'});
          wrapper.attr('popover-popup-close-delay', '2500');
        }
        $compile(wrapper)(scope);
        $compile(element)(scope);
      }
    }
  }
});

rcDirectives.directive('rcListSort', function ($timeout) {
  return {
    restrict: 'A',
    transclude: true,
    template :
    '<span class="clickable" ng-click="sortColumn()"><ng-transclude></ng-transclude>' +
    '  <i class="rc-list-sort-icon fa {{ reverse ? \'fa-chevron-down\' : \'fa-chevron-up\' }}" ng-show="showSort()"></i></span>',
    scope: {
      order: '=',
      by: '=',
      reverse : '=',
      update : '&'
    },
    link: function(scope, element, attrs) {
      scope.sortColumn = function () {
        if( scope.order === scope.by ) {
          scope.reverse = !!!scope.reverse
        } else {
          scope.by = scope.order ;
          scope.reverse = false;
        }
        $timeout(function () {
          scope.update && scope.update();
        }, 0);
      };

      scope.showSort = function () {
        return attrs.order === ('\'' + scope.by + '\'');
      };
    }
  };
});

rcDirectives.directive('rcEndpointUrl', function() {
  return {
    restrict: 'E',
    scope: {
      // id: '@',
      detailsLoaded: '=', // use this only to determine when the numberDetails object has arrived
      sidVar: '=',
      urlOnlyVar: '=',
      methodVar: '=',
      urlVar: '=',
      apps: '=',
    },
    controller: function ($scope) {
        $scope.appNameVar = "";
        $scope.setApp = function() {
            // if this is an application SID, populate the sidVar
            if ($scope.appNameVar && $scope.appNameVar.substr(0,2) == "AP") {
                $scope.sidVar = $scope.appNameVar;
            } else
            if (!$scope.appNameVar) {
                $scope.sidVar = null;
            }

        };

		$scope.setApplication = function (app) {
            $scope.appNameVar = app.projectName;
            $scope.sidVar = app.sid;
		};
		
		$scope.setMethod = function(method) {
			$scope.methodVar = method;
		};

        $scope.onTargetChanged = function(target) {
            if (target == "URL") {
                $scope.sidVar = null;
            } else if (target == "Application") {
                $scope.urlVar = null;
            }
        }

        $scope.initTarget = function() {
			if ($scope.urlOnlyVar)
				$scope.targetVar = 'URL';
			else {
				if($scope.sidVar){
				    $scope.targetVar = "Application";
				} else
				if ($scope.urlVar) {
					$scope.targetVar = "URL";
				} else {
				    $scope.targetVar = "Application";
				}
			}
			// initialize the application name if needed
			if ($scope.targetVar == "Application") {
			    $scope.appNameVar = $scope.sidVar; //name;
			}
        }

        // initialize control when numberDetails actually arrives.
        // NOTE: numberDetails is only used to signal when data has arrived and not to carry the actual data
        var clearWatch = $scope.$watch("detailsLoaded", function (newValue, oldValue) {
            if ($scope.detailsLoaded) {
                clearWatch();
                $scope.initTarget();
            }
        });

        $scope.targetVar = 'URL'; // default value until all data is in place (i.e. detailsLoaded == true)
	},
    templateUrl: 'templates/rc-endpoint-url.html'
  };
});
