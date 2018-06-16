'use strict';

angular.module('rcApp').controller('EventsCtrl', function ($rootScope, $state, Notifications, urlStateTracker, $location) {

  $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams, options) {
    // console.log('switching states: ' + fromState.name + ' -> ' + toState.name);
  });

  $rootScope.$on('$stateChangeError', function(event, toState, toParams, fromState, fromParams, error) {
    event.preventDefault();
    console.error('Error switching state: ' + fromState.name + ' -> ' + toState.name, error);
    // see AuthService.checkAccess() for error definitions
    if (error === 'MISSING_ACCOUNT_SID') {
      $state.go('public.login');
    }
    else if (error === 'RESTCOMM_AUTH_FAILED' || error === 'RESTCOMM_NOT_AUTHENTICATED') {
      // Notifications.error('Unauthorized access');
      urlStateTracker.remember($location);
      $state.go('public.login');
    }
    else if (error === 'RESTCOMM_ACCOUNT_NOT_INITIALIZED') {
      $state.go('public.uninitialized');
    }
    else if (error === 'ACCOUNT_ALREADY_INITIALIZED') {
      $state.go('restcomm.dashboard');
    }
    else if (error === 'UNKWNOWN_ERROR') {
      console.error('internal error');
    }
  });
});
