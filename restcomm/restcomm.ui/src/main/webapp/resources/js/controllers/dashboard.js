'use strict';

var rcMod = angular.module('rcApp');

rcMod.controller('DashboardCtrl', function ($scope, $resource, $rootScope, RCommStatistics, RCommLogsCalls, RCommLogsMessages) {

  $scope.ctrl = {
    slides: [
    '<p>Telestax is a proud sponsor of 2017 TADHack Global and TADSummit</p><p><img src="http://tadsummit.com/2017/wp-content/uploads/2017/06/TADS-2017-logo.png" width="90%"></p>',
    '<img src="https://secure.meetupstatic.com/photos/event/a/c/d/8/600_456464248.jpeg" align="left" width="120" hspace="10"> <h3>WEBINAR</h3><h5>Moving from Voice to Text increases Call Center Closure Rates by over 200%</h5><p class="pull-right">Learn more</p>',
    ]
  };

  var tfhParams = {'StartTime': new Date(Date.now() - 24*3600*1000).toISOString(), 'EndTime': new Date().toISOString() };
  RCommLogsCalls.search($.extend({accountSid: $scope.sid, PageSize: 99999999}, tfhParams), function(data) {
    $scope.nCalls = data.total;
    $scope.dCalls = 0;
    for(var call in data.calls) {
      $scope.dCalls += (data.calls[call].duration || 0);
    }
  });

  RCommLogsMessages.search($.extend({accountSid: $scope.sid, PageSize: 99999999}, tfhParams), function(data) {
    $scope.nMessages = data.total;
  });

  var startDate = new Date(new Date().setDate(new Date().getDate() - 30));
  var startDateJSON = startDate.toJSON().slice(0,10);
  var endDate = new Date();
  var endDateJSON = endDate.toJSON().slice(0,10);
  RCommStatistics.query({accountSid: $scope.sid, statName: 'Daily', StartDate: startDateJSON, EndDate: endDateJSON}, function(data) {
    $scope.callsData = [
      {
        key : 'Calls' ,
        bar: true,
        color: 'var(--primary-color)',
        values : []
      },
      {
        key : 'Minutes' ,
        color: 'var(--red-color)',
        'stroke-width': '5px',
        values : []
      }
    ];
    var maxDuration = 0;
    var maxCalls = 0;

    var lastDate = new Date(startDate);
    lastDate.setUTCHours(0,0,0,0);
    angular.forEach(data, function(value, key) {
      // fill with missing dates from last value to current
      while (lastDate < new Date(value.start_date)) {
        $scope.callsData[0].values.push([lastDate.getTime(), 0]);
        $scope.callsData[1].values.push([lastDate.getTime(), 0]);
        lastDate.setDate(lastDate.getDate() + 1);
      }
      lastDate.setDate(lastDate.getDate() + 1);

      $scope.callsData[0].values.push([new Date(value.start_date).getTime(), value.count || 0]);
      $scope.callsData[1].values.push([new Date(value.start_date).getTime(), value.usage || 0]);
      maxDuration = Math.max(maxDuration, value.usage);
      maxCalls = Math.max(maxCalls, value.count);
    });

    // fill with missing dates from last value to date range
    while (lastDate <= endDate) {
      $scope.callsData[0].values.push([lastDate.getTime(), 0]);
      $scope.callsData[1].values.push([lastDate.getTime(), 0]);
      lastDate.setDate(lastDate.getDate() + 1);
    }

    $scope.callsOptions.chart.bars.yDomain = [0, Math.max(parseInt(maxCalls * 1.1), 25)];
    $scope.callsOptions.chart.lines.yDomain = [0, Math.max(parseInt(maxDuration * 1.1), 100)];
  });

  RCommStatistics.query({accountSid: $scope.sid, statName: 'Daily', Category: 'SMS', StartDate: startDateJSON, EndDate: endDateJSON}, function(data) {
    $scope.smsData = [
      {
        key : 'Messages' ,
        bar: true,
        values : []
      }
    ];
    var maxSMS = 0;

    var smsLastDate = new Date(startDate);
    angular.forEach(data, function(value,key){
      // fill with missing dates from last value to current
      while (smsLastDate < new Date(value.start_date)) {
        $scope.smsData[0].values.push([smsLastDate.getTime(), 0]);
        smsLastDate.setDate(smsLastDate.getDate() + 1);
      }
      smsLastDate.setDate(smsLastDate.getDate() + 1);

      //console.log(value.start_date, value.count);
      $scope.smsData[0].values.push([new Date(value.start_date).getTime(), value.count || 0, value.start_date]);
      maxSMS = Math.max(maxSMS, value.count);
    });

    // fill with missing dates from last value to date range
    while (smsLastDate <= endDate) {
      $scope.smsData[0].values.push([smsLastDate.getTime(), 0]);
      smsLastDate.setDate(smsLastDate.getDate() + 1);
    }

    $scope.smsOptions.chart.yDomain = [0, Math.max(parseInt(maxSMS * 1.1), 100)];
  });

  $scope.callsOptions = {
    chart: {
      legendLeftAxisHint: '',
      legendRightAxisHint: '',
      type: 'linePlusBarChart',
      focusEnable: false,
      useInteractiveGuideline: true,
      height: 400,
      margin: {
        top: 30,
        right: 50,
        bottom: 100,
        left: 50
      },
      x: function(d, i){return d[0];},
      y: function(d){return d[1];},
      showValues: true,
      valueFormat: function (d) {
        return d3.format(',')(d);
      },
      transitionDuration: 500,
      xAxis: {
        axisLabel: '',
        showMaxMin: false,
        rotateLabels: 45,
        tickFormat: function(d, v) {
          return d3.time.format('%d-%m-%Y')(new Date(d));
        },
        tickValues: function(values) {
          return _.map(values[0].values, function(val, idx, arr) {
            return idx % 2 === 0 ? val[0] : (arr.length <= 16 ? val[0] : '');
          });
        }
      },
      y1Axis: {
        // axisLabel: 'Number of Calls',
      },
      y2Axis: {
        // axisLabel: 'Duration'
      },
      lines: { // for line chart
        yDomain: [0, 1000]
      },
      bars : {
        yDomain: [0, 100]
      },
      tooltipContent: function (key, x, y, e, graph) {
        return '<h3 class="text-center"><strong>' + e.point[0] + '</strong></h3>' +
          '<p>' +  y + ' ' + key.substr(0, key.indexOf('(')) + '</p>';
      },
    }
  };//end options});

  $scope.smsOptions = {
    chart: {
      type: "historicalBarChart",
      height: 400,
      margin: {
        top: 30,
        right: 50,
        bottom: 100,
        left: 50
      },
      x: function(d, i){ return d[0];},
      y: function(d){ return d[1];},
      useInteractiveGuideline: true,
      showValues: true,
      transitionDuration: 500,
      xAxis: {
        axisLabel: '',
        showMaxMin: false,
        rotateLabels: 45,
        tickFormat: function(d, v) {
          return d3.time.format('%d-%m-%Y')(new Date(d));
        },
        tickValues: function(values) {
          return _.map(values[0].values, function(val, idx, arr) {
            return idx % 2 === 0 ? val[0] : (arr.length <= 16 ? val[0] : '');
          });
        }
      },
      tooltipContent: function (key, x, y, e, graph) {
        return '<h3 class="text-center"><strong>' + e.point[0] + '</strong></h3>' +
          '<p>' +  y + ' ' + key + '</p>';
      }
    }
  };

  $scope.showSmallCharts = function () {
    $scope.callsOptions.chart.height = 200;
    $scope.callsOptions.chart.margin = {top: 10, left: 0, right: 0, bottom: 0};
    $scope.callsOptions.chart.showLegend = false;
    $scope.smsOptions.chart.height = 200;
    $scope.smsOptions.chart.margin = {top: 10, left: 0, right: 0, bottom: 0};
  }

});
