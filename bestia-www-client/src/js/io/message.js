/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Main message module. This module collects all message constructors so that a
 * massage can be easily created within the app.
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
		this.cmid = Bestia.Message._localMessageId++;
	},

	/**
	 * Tells the server to move the bestia to a certain spot on the map.
	 */
	BestiaMove : function(playerBestiaId, path, walkspeed) {
		this.mid = 'bestia.move';
		
		this.pbid = playerBestiaId;
		
		// Generate the path arrays.
		var pX = new Array(path.length);
		var pY = new Array(path.length);
		
		path.forEach(function(ele, i){
			pX[i] = ele.x;
			pY[i] = ele.y;
		});
		
		this.pX = pX;
		this.pY = pY;
		this.w = walkspeed;
	},

	/**
	 * Requests a complete sync with the inventory from the server.
	 */
	InventoryRequest : function() {
		this.mid = 'inventory.request';
	}
};
