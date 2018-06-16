'use strict';

var rcMod = angular.module('rcApp');

rcMod.controller('LogsMessagesCtrl', function ($scope, $resource, $timeout, $uibModal, SessionService, RCommLogsMessages) {

  $scope.Math = window.Math;

  $scope.sid = SessionService.get("sid");

  // search toggle only on mobile view
  $scope.showSearchToggle = window.outerWidth <= 768;

  // default search values
  $scope.search = {
    local_only: true,
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
    $scope.getMessagesList($scope.currentPage-1);
  };

  $scope.pageChanged = function() {
    $scope.getMessagesList($scope.currentPage-1);
  };

/*
  $scope.setPage = function(pageNo) {
    $scope.currentPage = pageNo;
  };
  */

  $scope.filter = function() {
    $timeout(function() { //wait for 'filtered' to be changed
      /* change pagination with $scope.filtered */
      $scope.noOfPages = Math.ceil($scope.filtered.length / $scope.entryLimit);
    }, 10);
  };

  // Modal : Message Details
  $scope.showMessageDetailsModal = function (message) {
    $uibModal.open({
      controller: 'LogsMessagesDetailsCtrl',
      scope: $scope,
      templateUrl: 'modules/modals/modal-logs-messages.html',
      resolve: {
        messageSid: function() {
          return message.sid;
        }
      }
    });
  };
  
  $scope.getMessagesList = function(page) {
    var params = $scope.search ? createSearchParams($scope.search) : {LocalOnly: true};
    RCommLogsMessages.search($.extend({accountSid: $scope.sid, Page: page, PageSize: $scope.entryLimit}, params), function(data) {
      $scope.messagesLogsList = data.messages;
      $scope.totalMessages = data.total;
      $scope.noOfPages = data.num_pages;
      $scope.start = parseInt(data.start) + 1;
      $scope.end = parseInt(data.end);
      if ($scope.end != $scope.totalMessages) {
        ++$scope.end;
      }
    });
  };
  
  var createSearchParams = function(search) {
    var params = {};

    // Mandatory fields
    if(search.start_time) {
      params["StartTime"] = search.start_time;
    }
    if(search.end_time) {
      params["EndTime"] = search.end_time;
    }
    if(search.from) {
      params["From"] = (search.from + '%');
    }
    if(search.to) {
      params["To"] = (search.to + '%');
    }
    if(search.body) {
      params["Body"] = search.body;
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
$scope.getMessagesList(0);
});

rcMod.controller('LogsMessagesDetailsCtrl', function($scope, $stateParams, $resource, $uibModalInstance, SessionService, RCommLogsMessages, messageSid) {
  $scope.sid = SessionService.get("sid");
  $scope.messageSid = $stateParams.messageSid || messageSid;

  $scope.closeMessageDetails = function () {
    $uibModalInstance.dismiss('cancel');
  };

  $scope.messageDetails = RCommLogsMessages.view({accountSid: $scope.sid, smsMessageSid: $scope.messageSid});
});
