'use strict';

var rcMod = angular.module('rcApp');

rcMod.controller('LogsNotificationsCtrl', function ($scope, $resource, $timeout, $uibModal, SessionService, RCommLogsNotifications) {

  $scope.Math = window.Math;

  $scope.sid = SessionService.get("sid");

  // search toggle only on mobile view
  $scope.showSearchToggle = window.outerWidth <= 768;

  // default search values
  $scope.search = {
    sub_accounts: false
  };

  // pagination support ----------------------------------------------------------------------------------------------

  $scope.currentPage = 1; //current page
  $scope.maxSize = 5; //pagination max size
  $scope.entryLimit = 10; //max rows for data table
  $scope.reverse = false;
  $scope.predicate = "date_created";

  $scope.setEntryLimit = function(limit) {
    $scope.entryLimit = limit;
    $scope.currentPage = 1;
    $scope.getNotificationsLogsList($scope.currentPage-1);
  };

/*
  $scope.setPage = function(pageNo) {
    $scope.currentPage = pageNo;
  };
  */

  $scope.pageChanged = function() {
      $scope.getNotificationsLogsList($scope.currentPage-1);
  };

  $scope.filter = function() {
    $timeout(function() { //wait for 'filtered' to be changed
      /* change pagination with $scope.filtered */
      $scope.noOfPages = Math.ceil($scope.filtered.length / $scope.entryLimit);
    }, 10);
  };

  // make sure no nulls remain in the 'query' filter.
  $scope.$watchCollection("query", function (query, oldvalue) {
    if (query) {
        if (query.error_code === null)
          delete query.error_code;
        if (query.date_created === null)
          delete query.date_created;
    }
    return query;
  });

  // Modal : Notification Details
  $scope.showNotificationDetailsModal = function (notification) {
    $uibModal.open({
      controller: 'LogsNotificationsDetailsCtrl',
      scope: $scope,
      templateUrl: 'modules/modals/modal-logs-notifications.html',
      windowClass: 'temp-modal-lg',
      resolve: {
        notificationSid: function() {
          return notification.sid;
        }
      }
    });
  };

  $scope.getNotificationsLogsList = function(page) {
    var params = $scope.search ? createSearchParams($scope.search) : {LocalOnly: true};
    RCommLogsNotifications.search($.extend({accountSid: $scope.sid, Page: page, PageSize: $scope.entryLimit}, params), function(data) {
      $scope.notificationsLogsList = data.notifications;
      $scope.totalNotifications = data.total;
      $scope.noOfPages = data.num_pages;
      $scope.start = parseInt(data.start) + 1;
      $scope.end = parseInt(data.end);
      if ($scope.end != $scope.totalNotifications) {
        ++$scope.end;
      }
    });
  }

  var createSearchParams = function(search) {
    var params = {};

    // Mandatory fields
    if(search.start_time) {
      params["StartTime"] = search.start_time;
    }
    if(search.end_time) {
      params["EndTime"] = search.end_time;
    }
    if(search.error_code) {
      params["ErrorCode"] = search.error_code;
    }
    if(search.request_url) {
      params["RequestUrl"] = ('%' + search.request_url + '%');
    }
    if(search.message_text) {
      params["MessageText"] = search.message_text;
    }
    $scope.hasCriteria = !_.isEmpty(params);

    return params;
  }

//Activate click event for date buttons.
 $scope.openDate = function(elemDate) {
   if (elemDate === "startDate") {
        angular.element('#startpicker').trigger('click');
   }else{
        angular.element('#endpicker').trigger('click');
   }
};

$scope.sort = function(item) {
        if ($scope.predicate == 'date_created') {
            return new Date(item.date_created);
        }
           return  item[$scope.predicate];
    };

$scope.sortBy = function(field) {
        if ($scope.predicate != field) {
            $scope.predicate = field;
            $scope.reverse = false;
        } else {
            $scope.reverse = !$scope.reverse;
        }
    };

  // initialize with a query
  $scope.getNotificationsLogsList(0);
});

rcMod.controller('LogsNotificationsDetailsCtrl', function($scope, $stateParams, $resource, $uibModalInstance, SessionService, RCommLogsNotifications, notificationSid) {
  $scope.sid = SessionService.get("sid");
  $scope.notificationSid = $stateParams.notificationSid || notificationSid;

  $scope.closeNotificationDetails = function () {
    $uibModalInstance.dismiss('cancel');
  };

  $scope.notificationDetails = RCommLogsNotifications.view({accountSid: $scope.sid, notificationSid:$scope.notificationSid});
});
