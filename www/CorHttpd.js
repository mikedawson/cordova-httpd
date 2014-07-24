
var argscheck = require('cordova/argscheck'),
    exec = require('cordova/exec');

var corhttpd_exports = {};

corhttpd_exports.startServer = function(options, success, error) {
	  var defaults = {
			    'www_root': '',
			    'port': 8888
			  };
	  
	  // Merge optional settings into defaults.
	  for (var key in defaults) {
	    if (typeof options[key] !== 'undefined') {
	      defaults[key] = options[key];
	    }
	  }
			  
  exec(success, error, "CorHttpd", "startServer", [ defaults['www_root'], defaults['port'] ]);
};

corhttpd_exports.stopServer = function(success, error) {
	  exec(success, error, "CorHttpd", "stopServer", []);
};

corhttpd_exports.getURL = function(success, error) {
	  exec(success, error, "CorHttpd", "getURL", []);
};

corhttpd_exports.getLocalPath = function(success, error) {
	  exec(success, error, "CorHttpd", "getLocalPath", []);
};

corhttpd_exports.mountDir = function(aliasPrefix, dirPath, success, error) {
    exec(success, error, "CorHttpd", "mountDir", [aliasPrefix, 
        dirPath]);
};

corhttpd_exports.registerHandler = function(aliasPrefix, success, error) {
    exec(success, error, "CorHttpd", "registerHandler", [aliasPrefix]);
};

corhttpd_exports.sendHandlerResponse = function(responseId, message, success, error) {
    exec(success, error, "CorHttpd", "sendHandlerResponse", 
        [responseId, message]);
}

module.exports = corhttpd_exports;

