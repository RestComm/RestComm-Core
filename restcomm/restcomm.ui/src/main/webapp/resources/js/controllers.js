'use strict';

/* Controllers */

// defining controllers module. Each module is defined at it's own file at resources/js/controllers/*.js
angular.module('rcApp.controllers', ['ui.bootstrap']);

// Shorter controllers are also defined here to avoid creating too many files.

// Parent 'restcomm' state controller. This state assumes the availability of an authenticated user
angular.module('rcApp.controllers').controller('RestcommCtrl', function ($scope,AuthService,authorize) {
    $scope.loggedAccount = AuthService.getAccount();
    $scope.accountProfile = AuthService.getProfile();
    $scope.sid = AuthService.getAccountSid();
});
