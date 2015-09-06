/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Inventory is hooking into inventory messages from the server and manages item
 * management. It lists items, updates the amount or removes them. It is also
 * responsible for making the user interact with the inventory like triggering
 * items or dropping them.
 * 
 * @constructor
 * @class Bestia.Inventory
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
Bestia.Inventory = function(pubsub) {

	var self = this;

	this._pubsub = pubsub;

	/**
	 * Holds all items delivered from the server.
	 * 
	 * @property
	 */
	this.items = ko.observableArray();
	
	/**
	 * Id of the currently selected bestia.
	 */
	this.currentBestiaId = 0;

	/**
	 * Handler if the server advises to completly re-display the inventory.
	 */
	var listHandler = function(_, data) {
		self.items.removeAll();
		data.pis.forEach(function(val) {
			self.items.push(new Bestia.ItemViewModel(val));
		});
	};

	pubsub.subscribe('inventory.list', listHandler);
	
	var bestiaSelectHandler = function(_, data) {
		// TODO Hier die ID übdaten wenn eine neue Bestia selektiert wird.
		self.currentBestiaId = data.bm.id;
	};
	
	pubsub.subscribe('bestia.info', bestiaSelectHandler);
	pubsub.subscribe('engine.selectBestia', bestiaSelectHandler);
	
	/**
	 * This function will try to use an item. In order to do this some sanity checks
	 * will be conducted: Is the item usable? If so the a request to the server will
	 * be send. This will usually trigger a inventory.update message to account for
	 * the changed item count. This function will do some sanity checks:
	 */
	this.useItem = function(item) {
		if(item.type() !== 'USABLE') {
			return;
		}
		
		var msg = new Bestia.Message.InventoryItemUse(item.playerItemId(), self.currentBestiaId);
		self._pubsub.publish('io.sendMessage', msg);
	};
};
