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
	app.model = app.Model || {};
	
	app.model.selectedBestia = ko.mapping.fromJS(data);
	ko.mapping.fromJS(data, app.model.selectedBestia);
	
	var onBestiaSelectHandler = function(_, data) {
		
	};
	
	$.subscribe('bestia.select', onBestiaSelectHandler);
	
	// Register for messages.
	$.subscribe('server.info', app.Server.Config.onMessageHandler);
})(Bestia, jQuery, ko);