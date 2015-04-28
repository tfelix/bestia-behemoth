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
	app.Config = {
		zones : ko.observableArray(),
		version: ko.observable(0),
		server: ko.observable(),
		connectedPlayer: ko.observable(0),
		resourceURL: ko.observable(''),
		debug: ko.observable(false),
		
		Engine : {
			tileSize : 32
		},
		
		/**
		 * Returns the correct URL to retrieve a certain resource from the server.
		 * 
		 * @param {string} The type of the resource to request [sound, map, tile, sprite, mob]
		 * @param {string} The unique name of the resource.
		 */
		makeUrl : function(type, name, nameExt) {
			var conf = app.server.Config;
			
			if(type == 'map') {
				return conf.resourceURL() + '/maps/' + name + '/' + name + '.json';
			} else if(type == 'tile') {
				return conf.resourceURL() + '/maps/' + name + '/' + nameExt;
			}
			
			throw "Type is not defined.";
		},
		
		onMessageHandler : function(_, msg) {
			console.log('New configuration message arrived! Setting values: ' + JSON.stringify(msg));
			
			var c = app.Config;
			c.zones(msg.z);
			c.version(msg.v);
			c.connectedPlayer(msg.cp);
			c.resourceURL(msg.res);
			c.server(msg.zn);
		}
	};
	
	// Register for messages.
	$.subscribe('server.info', app.Config.onMessageHandler);
	
	// Apply bindings AFTER the DOM has loaded.
	$(document).ready(function(){
		ko.applyBindings(app.server.Config, $('#server-info').get(0));
	});
})(Bestia, jQuery, ko);