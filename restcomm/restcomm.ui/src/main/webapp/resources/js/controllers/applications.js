angular.module('rcApp.controllers').controller('ApplicationsCtrl', function ($scope, RCommApplications, SessionService, $httpParamSerializer) {
    var accountSid = SessionService.get("sid");

    // only client-side sorting..
    $scope.predicate = 'friendly_name';
    $scope.reverse = false;

    $scope.appsList = RCommApplications.query({accountSid: accountSid, includeNumbers: true});

    $scope.commitNewName = function(app, newName) {
      console.log("renaming app to " + newName);
        RCommApplications.save({accountSid: accountSid, applicationSid: app.sid}, $httpParamSerializer({FriendlyName: newName}), function (result) {
          app.friendly_name = result.friendly_name;
        });

    }
});

angular.module('rcApp.controllers').controller('ApplicationDetailsCtrl', function ($scope, RCommApplications, RvdProjects, SessionService, $stateParams, $location, $dialog, Notifications, $filter, $httpParamSerializer, FileRetriever, PublicConfig) {
    var accountSid = SessionService.get("sid");
    var applicationSid = $stateParams.applicationSid;
    $scope.app = RCommApplications.get({accountSid: accountSid, applicationSid: applicationSid}, function () {
        $scope.provider = $filter('appProvider')($scope.app.rcml_url);
    }, function () {
        Notifications.error("Could not retrieve application " + applicationSid);
        $location.path("/applications");
    });
    // TODO also retrieve IncomingNumbers list for specific application
    // for now, we will extract the numbers from Application listing by filtering on the client side until proper
    // REST method support is provided
    // we populate $scope.app with 'numbers' property of filtered application element
    var allApps = RCommApplications.query({accountSid: accountSid, includeNumbers: true}, function() {
        if (Array.isArray(allApps) )
            for (var i=0; i < allApps.length; i++) {
                if (applicationSid == allApps[i].sid) {
                    $scope.app.numbers = allApps[i].numbers
                }
            }
    });


    $scope.confirmApplicationDelete = function(app) {
        confirmApplicationDelete(app, $dialog, $scope, Notifications, RCommApplications, RvdProjects, $location)
    };

    $scope.editInDesigner = function(app) {
        window.open(PublicConfig.rvdUrl + '/#/designer/' + app.sid + ($stateParams.firstTime ? '?firstTime=true' : ''));
    };

    $scope.saveExternalApp = function(app) {
        RCommApplications.save({accountSid: accountSid, applicationSid: applicationSid}, $httpParamSerializer({RcmlUrl: app.rcml_url}), function () {
            Notifications.success("Application '" + app.friendly_name + " ' saved");
            $location.path( "/applications" );
        });
    };

    $scope.downloadRvdApp = function(app) {
        var downloadUrl =  PublicConfig.rvdUrl + '/services/projects/' + app.sid + '/archive?projectName=' + app.friendly_name;
        FileRetriever.download(downloadUrl, app.friendly_name + ".zip").catch(function () {
            Notifications.error("Error downloading project archive");
        });
    }
});

angular.module('rcApp.controllers').controller('ApplicationCreationWizardCtrl', function ($scope, $rootScope, $location, Notifications) {

    $scope.onFileDropped = function(files) {
        // filename should end in .zip
        if (files[0]) {
            var m = files[0].name.match(RegExp("(.+)\\.zip$","i"));
            if ( ! (m && m[1]) ) {
                Notifications.error("This doesn't look like a .zip archive!");
                return;
            } else {
                $rootScope.droppedFiles = files;
                $location.path("/applications/new");
            }
        }
    }
});

angular.module('rcApp.controllers').controller('ApplicationCreationCtrl', function ($scope, $rootScope, $location, Notifications, RvdProjectImporter, RvdProjects, $state, $stateParams, RvdProjectTemplates, PublicConfig) {
    // the following variables are used as flags from the templates on the type of application creation: isExternalApp / droppedFiles / templateId
    $scope.templateId = $stateParams.templateId;
    var appOptions = {}, droppedFiles; // all options the application needs to be created like name, kind ... anything else ?
    if ( !!$rootScope.droppedFiles ) {
        droppedFiles = $rootScope.droppedFiles;
        delete $rootScope.droppedFiles;
    }

    if (!droppedFiles) {
        appOptions.kind = "voice"; // by default create voice applications
    }

    $scope.setKind = function(options, kind) {
        options.kind = kind;
    }

    // create project from template
    if ($scope.templateId) {
      if ($scope.templateId != 'BLANK') {
        $scope.template = RvdProjectTemplates.get({templateId:$scope.templateId}, function () {
          appOptions.kind = effectiveAppTemplateKind($scope.template); // update app kind based on template tags
          appOptions.name = $scope.template.name + " " + Math.floor(Date.now() / 1000);
        });
      } else {
        $scope.template = {id: 'BLANK', name: 'Blank', description: 'Empty application', tags: ['voice','sms','ussd']};
      }
    }

    function effectiveAppTemplateKind(template) {
      if (template) {
        if (template.tags.indexOf('voice') != -1)
          return 'voice';
        if (template.tags.indexOf('sms') != -1)
                  return 'sms';
        if (template.tags.indexOf('ussd') != -1)
                  return 'voice';
      }
    }

    $scope.createRvdApplication = function(options, templateId) {
      var params = {};
      if (!!templateId && templateId != 'BLANK') {
        // templateId is available, create from template
        params.template = templateId;
        params.name = options.name;
      } else {
        params.name = options.name;
        params.kind = options.kind;
      }
      RvdProjects.save(params, null, function (data) { // RVD does not have an intuitive API :-( // NOTE 'null' is VERY IMPORTANT here as it makes $resource and the kind as a query parameter
          Notifications.success("RVD application created");
          $state.go("restcomm.application-details", {applicationSid: data.sid, firstTime: true});
          //$location.path("/applications/" + data.sid + "?firstTime=true");
          window.open(PublicConfig.rvdUrl + '/#/designer/' + data.sid + '?firstTime=true');
      });
    }


    // if we're importing, use imported filename to suggest a name for the newly created project
    if (droppedFiles) {
        var m = droppedFiles[0].name.match(RegExp("(.+)\\.zip$", "i"));
        if ( m && m[1] ) {
            appOptions.name = m[1];
        } else {
            $scope.fileLooksWrong = true;
        }
    }

    $scope.importProjectFromFile = function(files, nameOverride) {
        if (files[0]) {
            RvdProjectImporter.import(files[0], nameOverride).then(function (result) {
                Notifications.success("Application '" + result.name + "' imported successfully");
                $location.path("/applications/" + result.id);
                window.open(PublicConfig.rvdUrl + '/#/designer/' + result.id);
            }, function (message) {
                Notifications.error(message);
            });
        }
    };

    $scope.appOptions = appOptions;
    $scope.droppedFiles = droppedFiles;
});


angular.module('rcApp.controllers').controller('ApplicationExternalCreationCtrl', function ($scope, RCommApplications, SessionService, $httpParamSerializer, Notifications, $location) {
    var accountSid = SessionService.get("sid");
    $scope.appOptions = { kind: "voice"}; // by default create voice applications
    $scope.isExternalApp = true; // flag this application as external to adapt the UI to it

    $scope.setKind = function(options, kind) {
        options.kind = kind;
    }

    $scope.createExternalApplication = function(app) {
        RCommApplications.save({accountSid: accountSid}, $httpParamSerializer({RcmlUrl: app.url, FriendlyName: app.name, Kind: app.kind}), function () {
            Notifications.success("Application '" + app.name + " ' created");
            $location.path( "/applications" );
        });
    }


});

var confirmApplicationDelete = function(app, $dialog, $scope, Notifications, RCommApplications, RvdProjects, $location) {
  var title = 'Delete application \'' + app.friendly_name + '\'';
  var msg = 'Are you sure you want to delete application ' + app.sid + ' (' + app.friendly_name +  ') ? This action cannot be undone.';
  var btns = [{result:'cancel', label: 'Cancel', cssClass: 'btn-default'}, {result:'confirm', label: 'Delete!', cssClass: 'btn-danger'}];

  $dialog.messageBox(title, msg, btns)
    .open()
    .then(function(result) {
      console.log("Provider: " + $scope.provider);
      if (result == "confirm") {
        if ($scope.provider == 'rvd') {
            RvdProjects.delete({applicationSid:app.sid}, {},
                function() {
                    Notifications.success('RestComm Application "' + app.friendly_name + '" has been deleted.');
                    $location.path( "/applications" );
                },
                function(response) {
                    if (response.status != 404) {
                      Notifications.error('Failed to delete application "' + app.friendly_name + '".');
                    } else {
                      $location.path( "/applications" );
                    }
                }
            );
        } else
        if ($scope.provider == 'external') {
            RCommApplications.delete({accountSid: app.account_sid, applicationSid:app.sid}, {},
               function() {
                    Notifications.success('RestComm Application "' + app.friendly_name + '" has been deleted.');
                    $location.path( "/applications" );
               },
               function() {
                   Notifications.error('Failed to delete application "' + app.friendly_name + '".');
               })
        }
      }
    });
};