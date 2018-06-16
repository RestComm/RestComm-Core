'use strict';

var rcMod = angular.module('rcApp');

rcMod.controller('LogsTranscriptionsCtrl', function ($scope, $resource, $timeout, $uibModal, SessionService, RCommLogsTranscriptions) {

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
    $scope.getTranscriptionsList($scope.currentPage-1);
  };

  $scope.pageChanged = function() {
    $scope.getTranscriptionsList($scope.currentPage-1);
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

  // Modal : Transcription Details
  $scope.showTranscriptionDetailsModal = function (transcription) {
    $uibModal.open({
      controller: 'LogsTranscriptionsDetailsCtrl',
      scope: $scope,
      templateUrl: 'modules/modals/modal-logs-transcriptions.html',
      resolve: {
        transcriptionSid: function() {
          return transcription.sid;
        }
      }
    });
  };

  $scope.getTranscriptionsList = function(page) {
    var params = $scope.search ? createSearchParams($scope.search) : {LocalOnly: true};
    RCommLogsTranscriptions.search($.extend({accountSid: $scope.sid, Page: page, PageSize: $scope.entryLimit}, params), function(data) {
      $scope.transcriptionsLogsList = data.transcriptions;
      $scope.totalTranscriptions = data.total;
      $scope.noOfPages = data.num_pages;
      $scope.start = parseInt(data.start) + 1;
      $scope.end = parseInt(data.end);
      if ($scope.end != $scope.totalTranscriptions) {
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
    if(search.transcription_text) {
      params["TranscriptionText"] = search.transcription_text;
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
  $scope.getTranscriptionsList(0);
});

rcMod.controller('LogsTranscriptionsDetailsCtrl', function($scope, $stateParams, $resource, $uibModalInstance, SessionService, RCommLogsTranscriptions, transcriptionSid) {
  $scope.sid = SessionService.get("sid");
  $scope.transcriptionSid = $stateParams.transcriptionSid || transcriptionSid;

  $scope.closeTranscriptionDetails = function () {
    $uibModalInstance.dismiss('cancel');
  };

  $scope.transcriptionDetails = RCommLogsTranscriptions.view({accountSid: $scope.sid, transcriptionSid:$scope.transcriptionSid});
});
