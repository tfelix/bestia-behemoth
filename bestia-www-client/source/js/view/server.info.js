'use strict';
/**
 * Listens for the server messages who announce the configuration. 
 * If such a message is encountered the data will be saved for the
 * system to use.
 * 
 * @module Server.Config
 */
var Bestia = window.Bestia = window.Bestia || {};
(function(app, $, ko) {
	/**
	 * Central configuration variables.
	 */
	app.server = {};
	app.server.Config = {
		zones : ko.observableArray(),
		version: ko.observable(0),
		connectedPlayer: ko.observable(0),
		resourceURL: ko.observable(''),
		debug: ko.observable(false),
		
		/**
		 * Returns the correct URL to retrieve a certain resource from the server.
		 * 
		 * @param {string} The type of the resource to request [sound, tile, sprite]
		 * @param {string} The unique name of the resource.
		 */
		makeUrl : function(type, name) {
			var conf = app.server.Config;
			return conf.resourceURL() + '/' + type + '/' + name;
		},
		
		onMessageHandler : function(_, msg) {
			console.log('New configuration message arrived! Setting values.');
			
			var conf = app.server.Config;
			
			conf.zones(msg.z);
			conf.version(msg.v);
			conf.connectedPlayer(msg.cp);
			conf.resourceURL(msg.res);
		}
	};
	
	// Register for messages.
	$.subscribe('server.info', app.server.Config.onMessageHandler);
	
	// Apply bindings AFTER the DOM has loaded.
	$(document).ready(function(){
		//ko.applyBindings(app.server.Config, $('#server-info').get(0));
	});
})(Bestia, jQuery, ko);