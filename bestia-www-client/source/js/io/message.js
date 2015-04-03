'use strict';
/**
 * Main message module. This module collects all message constructors so that a
 * massage can be easily created within the app.
 * 
 * @module io.Connection
 */

var Bestia = window.Bestia = window.Bestia || {};
(function(app, $) {
	/**
	 * Central configuration variables.
	 */
	app.message = app.message || {};
	app.message = {
		/**
		 * Asks the server for basic informations about the server instance.
		 */
		ServerInfo : function() {
			this.mid = 'server.info';
		},
		
		/**
		 * Asks the server to provide information about the bestia which are in
		 * posession.
		 */
		BestiaInfo : function() {
			this.mid = 'bestia.info';
		},
		
		Chat : function(mode, text, nick) {
			this.mid = 'chat.message';
			this.m = mode;
			this.txt = text;
			this.rxn = nick;
		}
	};

})(Bestia, jQuery);