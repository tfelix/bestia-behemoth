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
	 * <p>
	 * This property contains all items regardles of the set filter for the
	 * inventory. It is strongly discouraged to bind against this variable. It
	 * is kind of private but there may be uses. This property is more or less
	 * use cases.
	 * </p>
	 * <p>
	 * If a filter is set then the items in here are filtered and transfered to
	 * the items array. Update rate is limited once per 50 ms to optimize
	 * performance on big inventories.
	 * </p>
	 */
	this.allItems = ko.observableArray().extend({
		rateLimit : 50
	});

	/**
	 * If text is present in this variable the display of items inside the
	 * inventory is hidden if they dont start with this item name prefix.
	 */
	this.searchFilter = ko.observable('');

	/**
	 * Holds all items delivered from the server.
	 * 
	 * @property
	 */
	this.items = ko.pureComputed(function() {
		// Filter the item names in side the array with the set filter.
		var searchTxt = this.searchFilter();
		var items = self.allItems();
		items = items.filter(function(el) {
			return el.name().startsWith(searchTxt);
		});
		return items;
	}, this);

	/**
	 * Id of the currently selected bestia.
	 */
	this.currentBestiaId = 0;

	/**
	 * Handler if the server advises to re-render the inventory.
	 */
	var listHandler = function(_, data) {
		self.allItems.removeAll();
		data.pis.forEach(function(val) {
			self.allItems.push(new Bestia.ItemViewModel(val));
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
	 * This function will try to use an item. In order to do this some sanity
	 * checks will be conducted: Is the item usable? If so the a request to the
	 * server will be send. This will usually trigger a inventory.update message
	 * to account for the changed item count. This function will do some sanity
	 * checks:
	 */
	this.useItem = function(item) {
		if (item.type() !== 'USABLE') {
			return;
		}

		var msg = new Bestia.Message.InventoryItemUse(item.playerItemId(),
				self.currentBestiaId);
		self._pubsub.publish('io.sendMessage', msg);
	};
};
