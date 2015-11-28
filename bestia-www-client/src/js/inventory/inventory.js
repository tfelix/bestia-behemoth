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
	 * Holds the reference to the currently active bestia. We need this in order
	 * to obtain its id for the send out message and to set the item shortcuts.
	 * 
	 * @private
	 * @property
	 */
	this._selectedBestia = null;

	this.itemSlot1 = ko.observable(null);
	this.itemSlot2 = ko.observable(null);
	this.itemSlot3 = ko.observable(null);
	this.itemSlot4 = ko.observable(null);
	this.itemSlot5 = ko.observable(null);

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
	 * 
	 * @property {Bestia.ItemViewModel}
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
	this.selectedItem = ko.observable(null);

	/**
	 * Flag to show and hide the inventory window.
	 */
	this.showWindow = ko.observable(false);

	/**
	 * Show the current weight of all items inside the inventory.
	 * 
	 * @property {Number}
	 * @public
	 */
	this.currentWeight = ko.pureComputed(function() {
		var weight = 0;
		self.allItems().forEach(function(el) {
			weight += el.totalWeight();
		});
		return weight;
	});

	/**
	 * Shows the maximum weight the bestia can carry.
	 * 
	 * @property {Number}
	 * @public
	 */
	this.maxWeight = ko.observable(0);

	/**
	 * Holds all items delivered from the server.
	 * 
	 * @property
	 */
	this.items = ko.pureComputed(function() {
		var items = self.allItems();

		// Filter categories.
		var catFilter = this.categoryFilter();

		if (catFilter !== '') {
			items = items.filter(function(el) {
				return el.type().toLowerCase() === catFilter;
			});
		}

		// Filter the item names in side the array with the set filter.
		var searchTxt = this.searchFilter();

		items = items.filter(function(el) {
			if (el.name === undefined) {
				return false;
			}

			return el.name().lastIndexOf(searchTxt, 0) === 0;
		});

		return items;
	}, this);

	/**
	 * Handler if the server advises to re-render the inventory.
	 */
	var listHandler = function(_, data) {
		var newItems = [];
		self.allItems.removeAll();

		data.pis.forEach(function(val) {
			var item = new Bestia.ItemViewModel(val);
			newItems.push(item);
			self.allItems.push(item);
		});

		self._translateItems(newItems);

		self.maxWeight(data.mw);
	};
	pubsub.subscribe('inventory.list', listHandler);

	/**
	 * Updates the item via an update message from the server.
	 */
	var updateHandler = function(_, data) {
		var newItems = [];
		data.pis.forEach(function(val) {
			var item = self._findItem(val.i.id);

			if (item === null) {
				// Add the new item to the inventory.
				var newItem = new Bestia.ItemViewModel(val);
				self.allItems.push(newItem);
				newItems.push(newItem);
			} else {
				var newAmount = item.amount() + val.a;
				if (newAmount <= 0) {
					// Remove item.
					self.allItems.remove(item);
				} else {
					// Update amount.
					item.amount(newAmount);
					// Send notifications for other sub systems.
					pubsub.publish(Bestia.Signal.INVENTORY_ITEM_ADD, item);
				}
			}
		});

		if (newItems.length > 0) {
			self._translateItems(newItems);
		}
	};
	pubsub.subscribe('inventory.update', updateHandler);

	/**
	 * Saves the new bestia id of the currently selected bestia.
	 * 
	 * @param {Bestia.BestiaViewModel}
	 *            bestia - The newly selected bestia.
	 */
	var bestiaSelectHandler = function(_, bestia) {
		self._selectedBestia = bestia;
		
		// Set the item shortcuts by the ones of the newly selected bestia.
		self.itemSlot1(bestia.item1());
		self.itemSlot2(bestia.item2());
		self.itemSlot3(bestia.item3());
		self.itemSlot4(bestia.item4());
		self.itemSlot5(bestia.item5());
	};
	pubsub.subscribe(Bestia.Signal.BESTIA_SELECTED, bestiaSelectHandler);

	/**
	 * Selects the clicked/touched item. Further details and options regarding
	 * this item are displayed.
	 */
	this.clickItem = function(item) {
		self.selectedItem(item);
		self.dropAmount(item.amount());
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

		var msg = new Bestia.Message.InventoryItemUse(item.playerItemId(), self.currentBestiaId);
		self._pubsub.send(msg);
	};

	this.dropAmount = ko.observable(1);

	/**
	 * This will send a drop request for the selected item and the server.
	 * 
	 * @param {Bestia.ItemVoewModel}
	 *            item - Item to be dropped.
	 * @param {Number}
	 *            amount - Amount wished to be dropped.
	 */
	this.dropItem = function() {
		var msg = new Bestia.Message.InventoryItemDrop(self.selectedItem().itemId(), self.dropAmount(),
				self.currentBestiaId);
		self._pubsub.send(msg);

	};

	/**
	 * Deletes an item from the shortcut list and saves this list to the server.
	 * 
	 * @param {Numeric}
	 *            slot - The slot to be deleted.
	 */
	this.removeItem = function(slot) {
		switch (slot) {
		case 1:
			self.itemSlot1(null);
			self._selectedBestia.item1(null);
			break;
		case 2:
			self.itemSlot2(null);
			self._selectedBestia.item2(null);
			break;
		case 3:
			self.itemSlot3(null);
			self._selectedBestia.item3(null);
			break;
		case 4:
			self.itemSlot4(null);
			self._selectedBestia.item4(null);
			break;
		case 5:
			self.itemSlot5(null);
			self._selectedBestia.item5(null);
			break;
		}
	};

	/**
	 * Binds the item to use as a shortcut.
	 * 
	 * @param {Numeric}
	 *            slot - Between 1 and 6.
	 * @param {Bestia.ItemViewModel}
	 *            item - The item to be selected.
	 */
	this.bindItem = function(slot) {

		var item = self.selectedItem();

		if (!item) {
			throw "Item can not be undefined.";
		}

		switch (slot) {
		case 1:
			self.itemSlot1(item);
			self._selectedBestia.item1(item);
			break;
		case 2:
			self.itemSlot2(item);
			self._selectedBestia.item2(item);
			break;
		case 3:
			self.itemSlot3(item);
			self._selectedBestia.item3(item);
			break;
		case 4:
			self.itemSlot4(item);
			self._selectedBestia.item4(item);
			break;
		case 5:
			self.itemSlot5(item);
			self._selectedBestia.item5(item);
			break;
		default:
			throw "Slot must be between 1 and 5.";
		}

		self.saveItemBindings();
	};

};

/**
 * Internal method to translate item names and desciptions. Awaits an array with
 * item models to translate.
 * 
 * @private
 * @param {Array[Bestia.ItemViewModel]}
 *            items - Array of item view models to translate.
 */
Bestia.Inventory.prototype._translateItems = function(items) {
	var buildTranslationKeyName = function(item) {
		return 'item.' + item.itemDatabaseName();
	};

	var buildTranslationKeyDesc = function(item) {
		return 'item.' + item.itemDatabaseName() + '_desc';
	};

	var i18nKeys = items.map(buildTranslationKeyName);
	i18nKeys = i18nKeys.concat(items.map(buildTranslationKeyDesc));

	this._i18n.t(i18nKeys, function(t) {
		items.forEach(function(val) {

			var nameTrans = t(buildTranslationKeyName(val));
			var descTrans = t(buildTranslationKeyDesc(val));

			val.name(nameTrans);
			val.description(descTrans);
		});
	});
};

/**
 * Looks for the item in the current items array. If it is found the
 * {Bestia.Inventory.ItemViewModel} is returned. Null otherwise.
 * 
 * @private
 * @param itemId
 * @returns The {Bestia.Inventory.ItemViewModel} if found or null otherwise.
 */
Bestia.Inventory.prototype._findItem = function(itemId) {
	var items = this.allItems();
	for (var i = 0; i < items.length; i++) {
		if (items[i].itemId() == itemId) {
			return items[i];
		}
	}
	return null;
};

/**
 * Shows the inventory window.
 */
Bestia.Inventory.prototype.show = function() {
	this.showWindow(true);
};

/**
 * Hides the inventory window.
 */
Bestia.Inventory.prototype.close = function() {
	this.showWindow(false);
};

/**
 * Sends the item bindings of the currently selected bestia to the server.
 */
Bestia.Inventory.prototype.saveItemBindings = function() {
	var piId1 = this.itemSlot1() ? this.itemSlot1().playerItemId() : null;
	var piId2 = this.itemSlot2() ? this.itemSlot2().playerItemId() : null;
	var piId3 = this.itemSlot3() ? this.itemSlot3().playerItemId() : null;
	var piId4 = this.itemSlot4() ? this.itemSlot4().playerItemId() : null;
	var piId5 = this.itemSlot5() ? this.itemSlot5().playerItemId() : null;
	var bestiaId = this._selectedBestia.playerBestiaId();
	var msg = new Bestia.Message.ItemSet(bestiaId, piId1, piId2, piId3, piId4, piId5);
	this._pubsub.publish('io.sendMessage', msg);
};
