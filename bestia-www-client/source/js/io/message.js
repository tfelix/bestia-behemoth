/**
 * Main message module. This module collects all message constructors so that a
 * massage can be easily created within the app.
 * 
 * @module io.Connection
 */
(function(app) {
	'use strict';
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

		Chat : function(mode, text, nick, senderNick) {
			this.mid = 'chat.message';
			this.m = mode;
			this.txt = text;
			this.rxn = nick;
			this.sn = senderNick || '';
			this.lmid = app.message.Chat.localMessageId++;
		}
	};

	// Static counter for local message id which is needed if the server gives
	// an error about a send message and the client needs to reference it back
	// again.
	app.message.Chat.localMessageId = 0;

})(Bestia, jQuery);