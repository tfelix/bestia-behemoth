'use strict';
/**
 * Listens for the various system messages and handles them.
 * 
 * @module System
 */
var Bestia = window.Bestia = window.Bestia || {};
(function(app, $) {
	/**
	 * Central configuration variables.
	 */
	app.System = {};
	
	/**
	 * Handler for system.pong message.
	 */
	app.System.onPongHandler = function(msg) {
		console.log('Pong Message received: ' + msg.m);
	}
	$.subscribe('system.pong', app.System.onPongHandler);
	
	/**
	 * Handler for system.error message.
	 */
	app.System.onErrorHandler = function(msg) {
		console.error('Error: ' + msg.txt + ' Code:' + msg.ec);
	}
	$.subscribe('system.error', app.System.onErrorHandler);
	
})(Bestia, jQuery);