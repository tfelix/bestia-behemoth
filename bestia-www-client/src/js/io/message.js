/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Main message module. This module collects all message constructors so that a
 * massage can be easily created within the app.
 * 
 * @class Bestia.Message
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

		path.forEach(function(ele, i) {
			pX[i] = ele.x;
			pY[i] = ele.y;
		});

		this.pX = pX;
		this.pY = pY;
		this.w = walkspeed;
	},

	/**
	 * Orders the server to switch to another active bestia in order to get
	 * client updates now from it.
	 */
	BestiaActivate : function(playerBestiaId) {
		this.mid = 'bestia.activate';

		this.pbid = playerBestiaId;
	},
	
	/**
	 * Uses an attack.
	 */
	AttackUse : function(attackId, x, y) {
		this.aid = attackId;
		this.x = x;
		this.y = y;
	},

	/**
	 * Requests a complete sync with the inventory from the server.
	 */
	InventoryRequest : function() {
		this.mid = 'inventory.request';
	},

	/**
	 * Advises the server to use an item. The response depends on which item was
	 * used. A simple consumable item will simply trigger its effect and apply
	 * it to the currently selected bestia. But the item might also trigger a
	 * script response which leads to download of additional scripts from the
	 * server or an effect on the map itself.
	 * 
	 * @param {Number}
	 *            playerItemId - The player item id of the item to be used.
	 * @param {Number}
	 *            playerBestiaId - The id of the currently selected bestia.
	 */
	InventoryItemUse : function(playerItemId, playerBestiaId) {
		this.mid = 'inventory.item.use';

		this.pid = playerItemId;
		this.pbid = playerBestiaId;
	},
	
	/**
	 * Requests to the server to list all available attacks for this bestia.
	 */
	AttackListRequest : function() {
		this.mid = 'attack.list.request';
	},
	
	/**
	 * Sends a translation request to the server. Category and 
	 */
	TranslationRequest : function(items, token) {
		this.mid = 'translation.request';
		
		this.is = [];
		this.t = token;
		
		// Translate the items.
		if(!Array.isArray(items)) {
			return;
		}
		
		for(var i = 0; i < items.length; i++) {
			if(!items[i].hasOwnProperty('cat') || !items[i].hasOwnProperty('key')) {
				continue;
			}
			this.is.push({c: items[i].cat.toUpperCase(), k: items[i].key});
		}
	}
};
