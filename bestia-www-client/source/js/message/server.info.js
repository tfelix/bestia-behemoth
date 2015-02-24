'use strict';
/**
 * Listens for the server messages who announce the configuration. 
 * If such a message is encountered the data will be saved for the
 * system to use.
 * 
 * @module Server.Config
 */
var Bestia = window.Bestia = window.Bestia || {};
(function(app, $) {
	/**
	 * Central configuration variables.
	 */
	app.Server = {};
	app.Server.Config = {
		zones : [],
		version: null,
		connectedPlayer: 0,
		resourceURL: null,
		
		/**
		 * Returns the correct URL to retrieve a certain resource from the server.
		 * 
		 * @param {string} The type of the resource to request [sound, tile, sprite]
		 * @param {string} The unique name of the resource.
		 */
		makeUrl : function(type, name) {
			var conf = app.Server.Config;
			return conf.resourceURL + '/' + type + '/' + name;
		},
		
		onMessageHandler : function(_, msg) {
			console.log('New configuration message arrived! Setting values.');
			
			var conf = app.Server.Config;
			
			conf.zones = msg.z;
			conf.version = msg.v;
			conf.connectedPlayer = msg.cp;
			conf.resourceURL = msg.res;
		}
	};
	
	// Register for messages.
	$.subscribe('server.info', app.Server.Config.onMessageHandler);
})(Bestia, jQuery);