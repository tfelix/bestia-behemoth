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
 * @param {Bestia.PubSub} pubsub - Publish/Subscriber interface.
 */
Bestia.Inventory = function(pubsub) {

	var self = this;
	
	this._pubsub = pubsub;
	
	/**
	 * Holds all items delivered from the server.
	 * @property
	 */
	this.items = ko.observableArray();
	
	/**
	 * Handler if the server advises to completly re-display the inventory.
	 */
	var listHandler = function(_, data) {
		self.items.removeAll();
		data.pis.forEach(function(val){
			self.items.push(new Bestia.ItemViewModel(val));
		});
	};
	
	pubsub.subscribe('inventory.list', listHandler);
};

