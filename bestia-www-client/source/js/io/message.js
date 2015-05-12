/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */
(function(Bestia) {
	'use strict';

	/**
	 * Main message module. This module collects all message constructors so
	 * that a massage can be easily created within the app.
	 * 
	 * @module io.Connection
	 */
	Bestia.Message = {

		// Static counter for local message id which is needed if the server
		// gives an error about a send message and the client needs to
		// reference it back again.
		_localMessageId : 0,

		/**
		 * Asks the server for basic informations about the server instance.
		 * 
		 * @constructor
		 */
		ServerInfo : function() {
			this.mid = 'server.info';
		},

		/**
		 * Asks the server to provide information about the bestia which are in
		 * posession.
		 * 
		 * @constructor
		 */
		BestiaInfo : function() {
			this.mid = 'bestia.info';
		},

		/**
		 * Creates a chat message.
		 * 
		 * @constructor
		 */
		Chat : function(mode, text, nick, senderNick) {
			this.mid = 'chat.message';
			this.m = mode;
			this.txt = text;
			this.rxn = nick;
			this.sn = senderNick || '';
			this.lmid = Bestia.Message._localMessageId++;
		},
		
		/**
		 * Requests a complete sync with the inventory from the server.
		 */
		InventoryRequest : function() {
			this.mid = 'inventory.request';
		}
	};

})(Bestia);