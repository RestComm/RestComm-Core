'use strict';

/* Filters */

var rcFilters = angular.module('rcApp.filters', []);

rcFilters.filter('startFrom', function() {
  return function(input, start) {
    if(input) {
      start = +start; //parse to int
      return input.slice(start);
    }
    return [];
  }
});

rcFilters.filter('newlines', function () {
  return function(text) {
    return text != null ? unescape(text).replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/&/g, '\n').replace(/\+/g, ' ') : null;
  }
});

rcFilters.filter('noHTML', function () {
  return function(text) {
    return text != null ? text
      .replace(/&/g, '&amp;')
      .replace(/>/g, '&gt;')
      .replace(/</g, '&lt;') : null;
  }
});

rcFilters.filter('toDuration', function () {
  return function(timeInSeconds) {
    if (timeInSeconds < 60) {
      return timeInSeconds + 's';
    }
    else if (timeInSeconds < 60 * 60) {
      var minutes = parseInt(timeInSeconds / 60, 10);
      var seconds = timeInSeconds % 60;
      return minutes + 'm' + seconds + 's';
    }
    else {
      var hours = parseInt(timeInSeconds / 60 / 60, 10);
      var minutes = parseInt((timeInSeconds - (hours * 60 * 60)) / 60, 10);
      // var seconds = timeInSeconds % 60;
      return hours + 'h' + minutes + 'm'; // + seconds + 's';
    }
  }
});

rcFilters.filter("toDate", [ '$filter', function ($filter) {
  return function(dateStr, format) {
    return $filter('date')(new Date(dateStr), format);
  }
}]);

rcFilters.filter("timeago", function () {
  //time: the time
  //local: compared to what time? default: now
  //raw: if you want in a format of "5 minutes ago", or "5 minutes"
  return function (time, local, raw) {
    if (!time) return "never";

    if (!local) {
      (local = Date.now())
    }

    if (angular.isDate(time)) {
      time = time.getTime();
    } else if (typeof time === "string") {
      time = new Date(time).getTime();
    }

    if (angular.isDate(local)) {
      local = local.getTime();
    }else if (typeof local === "string") {
      local = new Date(local).getTime();
    }

    if (typeof time !== 'number' || typeof local !== 'number') {
      return;
    }

    var
      offset = Math.abs((local - time) / 1000),
      span = [],
      MINUTE = 60,
      HOUR = 3600,
      DAY = 86400,
      WEEK = 604800,
      //MONTH = 2629744,
      YEAR = 31556926,
      DECADE = 315569260;

    if (offset <= MINUTE)              span = [ '', raw ? 'now' : 'less than a minute' ];
    else if (offset < (MINUTE * 60))   span = [ Math.round(Math.abs(offset / MINUTE)), 'minute' ];
    else if (offset < (HOUR * 24))     span = [ Math.round(Math.abs(offset / HOUR)), 'hour' ];
    else if (offset < (DAY * 7))       span = [ Math.round(Math.abs(offset / DAY)), 'day' ];
    else if (offset < (WEEK * 52))     span = [ Math.round(Math.abs(offset / WEEK)), 'week' ];
    //else if (offset < (MONTH * 12))    span = [ Math.round(Math.abs(offset / WEEK)), 'month' ];
    else if (offset < (YEAR * 10))     span = [ Math.round(Math.abs(offset / YEAR)), 'year' ];
    else if (offset < (DECADE * 100))  span = [ Math.round(Math.abs(offset / DECADE)), 'decade' ];
    else                               span = [ '', 'a long time' ];

    span[1] += (span[0] === 0 || span[0] > 1) ? 's' : '';
    span = span.join(' ');

    if (raw === true) {
      return span;
    }
    return (time <= local) ? span + ' ago' : 'in ' + span;
  }
});

rcFilters.filter('bytes', function() {
  return function(bytes, precision) {
    if (isNaN(parseFloat(bytes)) || !isFinite(bytes)) return '-';
    if (typeof precision === 'undefined') precision = 1;
    var units = ['bytes', 'KB', 'MB', 'GB', 'TB', 'PB'],
      number = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, Math.floor(number))).toFixed(precision) +  ' ' + units[number];
  }
});

rcFilters.filter('toTrusted', ['$sce', function($sce){
        return function(text) {
            return $sce.trustAsHtml(text);
        };
}]);

// Determines whether an rcml-url refers to an 'RVD' or an 'external' application and returned
rcFilters.filter('appProvider', function (PublicConfig) {
    var r = PublicConfig.rvdUrl + '/';
    return function(rcmlUrl) {
        if (!!rcmlUrl && rcmlUrl.match(r))
            return "rvd";
        else
            return "external";
    }
});


// converts a date string to unixDate
rcFilters.filter('unixDate', function () {
    return function(dateString) {
        if (dateString) {
            var d = new Date(dateString);
            return d;
        }
        return dateString;
    }
});


