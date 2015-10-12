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
 * @param {Bestia.I18n}
 *            i18n - Translation interface for translating items.
 */
Bestia.Inventory = function(pubsub, i18n) {

	var self = this;

	this._pubsub = pubsub;

	/**
	 * i18n interface to translate items.
	 * 
	 * @property
	 * @private
	 */
	this._i18n = i18n;

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
	 * If this filter is set to a certain category ('usable', 'quest', 'etc',
	 * 'equip') only items of this category is displayed. Can be used together
	 * with the searchFilter property.
	 */
	this.categoryFilter = ko.observable('');

	/**
	 * The item on which was clicked will be displayed in detail to perform
	 * certain activities with if (display description, drop menu etc.)
	 */
	this.selectedItem = ko.observable();

	/**
	 * Holds all items delivered from the server.
	 * 
	 * @property
	 */
	this.items = ko.pureComputed(function() {
		var items = self.allItems();
		
		// Filter categories.
		var catFilter = this.categoryFilter();
		
		if(catFilter !== '') {
			items = items.filter(function(el) {
				return el.type().toLowerCase() === catFilter;
			});
		}
		
		// Filter the item names in side the array with the set filter.
		var searchTxt = this.searchFilter();
		
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
		// var i18nPrefix = 'item.';
		self.allItems.removeAll();

		data.pis.forEach(function(val) {
			var item = new Bestia.ItemViewModel(val);
			self.allItems.push(item);
		});

		/*
		 * data.pis.forEach(function(val) { var item = new
		 * Bestia.ItemViewModel(val);
		 * 
		 * newItems.push(item); self.allItems.push(item); }, this);
		 * 
		 * var i18nKeys = newItem.map(function(el){ return i18nPrefix +
		 * el.itemDatabaseName(); }, this);
		 */

		// Translate the item names.
		self._i18n.t('item.apple', function(t) {
			alert(t('item.apple'));
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
	 * Selects the clicked/touched item. Further details and options regarding
	 * this item are displayed.
	 */
	this.clickItem = function(item) {
		this.selectedItem(item);
	};

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
