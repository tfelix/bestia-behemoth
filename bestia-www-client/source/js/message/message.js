'use strict';
/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
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
		 * Asks the server to provide information about the bestia 
		 * which are in posession.
		 */
		BestiaInfo : function() {
			this.mid = 'bestia.info';
		}
	};



})(Bestia, jQuery);