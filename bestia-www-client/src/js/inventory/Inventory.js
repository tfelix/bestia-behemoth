/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from '../io/Signal.js';
import MID from '../io/messages/Ids.js';
import Message from '../io/messages/Message.js';
import InputEvent from '../input/Input.js';
import ItemViewModel from './ItemViewModel.js';

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
 * @param {Bestia.UrlHelper}
 *            urlHelper - Helper to resolve urls into the bestia asset storage.
 */
export default class Inventory {
	
	constructor(pubsub, i18n, urlHelper) {

		if (pubsub === undefined) {
			throw "pubsub can not be null.";
		}
		if (i18n === undefined) {
			throw "i18n can not be null.";
		}
		if (urlHelper === undefined) {
			throw "urlHelper can not be null.";
		}
	
		var self = this;
	
		this._urlHelper = urlHelper;
	
		/**
		 * 
		 * @param {Bestia.Pubsub}
		 */
		this._pubsub = pubsub;
	
		/**
		 * i18n interface to translate items.
		 * 
		 * @property
		 * @private
		 */
		this._i18n = i18n;
	
		/**
		 * Flag if the inventory has been loaded with new items.
		 * 
		 * @property {boolean}
		 * @public
		 */
		this.hasLoaded = ko.observable(false);
	
		/**
		 * Holds the reference to the currently active bestia. We need this in
		 * order to obtain its id for the send out message and to set the item
		 * shortcuts.
		 * 
		 * @private
		 * @property
		 */
		this._selectedBestia = null;
		
		/**
		 * Amount of the item to be dropped.
		 * 
		 * @public
		 * @property {Number}
		 */
		this.dropAmount = ko.observable(1);
	
		this.itemSlot1 = ko.observable(null);
		this.itemSlot2 = ko.observable(null);
		this.itemSlot3 = ko.observable(null);
		this.itemSlot4 = ko.observable(null);
		this.itemSlot5 = ko.observable(null);
	
		/**
		 * Highlight class for the item slots.
		 */
		this.itemSlot1Css = ko.observable(null);
		this.itemSlot2Css = ko.observable(null);
		this.itemSlot3Css = ko.observable(null);
		this.itemSlot4Css = ko.observable(null);
		this.itemSlot5Css = ko.observable(null);
	
		/**
		 * <p>
		 * This property contains all items regardles of the set filter for the
		 * inventory. It is strongly discouraged to bind against this variable.
		 * It is kind of private but there may be uses. This property is more or
		 * less use cases.
		 * </p>
		 * <p>
		 * If a filter is set then the items in here are filtered and transfered
		 * to the items array. Update rate is limited once per 50 ms to optimize
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
		this.searchFilter = ko.observable('').extend({
			throttle : 100
		});
	
		/**
		 * If this filter is set to a certain category ('usable', 'quest',
		 * 'etc', 'equip') only items of this category is displayed. Can be used
		 * together with the searchFilter property.
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
		}, this).extend({
			throttle : 1
		});
	
		// ######## PUBSUB HANDLER ########
		pubsub.subscribe(MID.INVENTORY_LIST, this._handleList.bind(this));
		pubsub.subscribe(MID.INVENTORY_UPDATE, this._handleUpdate.bind(this));
	
		/**
		 * Received event probably from the input controller to perform a
		 * casting of the item.
		 */
		pubsub.subscribe(Signal.INPUT_USE_ITEM, this._handlerInputCastItem.bind(this));
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerBestiaSelected.bind(this));
		pubsub.subscribe(Signal.INVENTORY_PERFORM_CAST, this._handlerDoCast.bind(this));
		// pubsub.subscribe(Bestia.Signal.INVENTORY_CAST_CONFIRM,
		// this._handlerCastServerConfirm.bind(this));
	}
	
	/**
	 * Selects the clicked/touched item. Further details and options regarding
	 * this item are displayed.
	 */
	selectItem(item) {
		this.selectedItem(item);
		this.dropAmount(item.amount());
	}

	/**
	 * This function will try to use an item. In order to do this some sanity
	 * checks will be conducted: Is the item usable? If so the a request to the
	 * server will be send. This will usually trigger a inventory.update message
	 * to account for the changed item count. This function will do some sanity
	 * checks:
	 */
	useItem(item) {

		item = item || this.selectedItem();

		if (item === undefined) {
			console.debug("No item selected.");
			return;
		}

		if (item.type() === 'USABLE') {
			// Just send the server the message to directly use this item.
			var msg = new Message.InventoryItemCast(item.itemId(), this._selectedBestia.playerBestiaId());
			this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
		} else if (item.type() === 'CASTABLE') {
			// Item is "castable". Notify the engine about displaying a
			// indicator how to use this item.
			this._pubsub.publish(Signal.ENGINE_CAST_ITEM, item);
		}
	}

	/**
	 * This will send a drop request for the selected item and the server.
	 * 
	 * @param {Bestia.ItemVoewModel}
	 *            item - Item to be dropped.
	 * @param {Number}
	 *            amount - Amount wished to be dropped.
	 */
	dropItem() {
		var msg = new Message.InventoryItemDrop(this.selectedItem().itemId(), this.dropAmount(),
				this.currentBestiaId);
		this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);

	}

	/**
	 * Deletes an item from the shortcut list and saves this list to the server.
	 * 
	 * @param {Numeric}
	 *            slot - The slot to be deleted.
	 */
	unbindItem(slot) {
		switch (slot) {
		case 1:
			this.itemSlot1(null);
			this._selectedBestia.item1(null);
			break;
		case 2:
			this.itemSlot2(null);
			this._selectedBestia.item2(null);
			break;
		case 3:
			this.itemSlot3(null);
			this._selectedBestia.item3(null);
			break;
		case 4:
			this.itemSlot4(null);
			this._selectedBestia.item4(null);
			break;
		case 5:
			this.itemSlot5(null);
			this._selectedBestia.item5(null);
			break;
		}

		this.saveItemBindings();
	}

	/**
	 * Binds the item to use as a shortcut.
	 * 
	 * @param {Numeric}
	 *            slot - Between 1 and 6.
	 * @param {Bestia.ItemViewModel}
	 *            item - The item to be selected.
	 */
	bindItem(slot) {

		var item = this.selectedItem();

		if (!item) {
			throw "Item can not be undefined.";
		}

		switch (slot) {
		case 1:
			this.itemSlot1(item);
			this._selectedBestia.item1(item);
			break;
		case 2:
			this.itemSlot2(item);
			this._selectedBestia.item2(item);
			break;
		case 3:
			this.itemSlot3(item);
			this._selectedBestia.item3(item);
			break;
		case 4:
			this.itemSlot4(item);
			this._selectedBestia.item4(item);
			break;
		case 5:
			this.itemSlot5(item);
			this._selectedBestia.item5(item);
			break;
		default:
			throw "Slot must be between 1 and 5.";
		}

		this.saveItemBindings();
	}
	
	_handlerInputCastItem(_, slotN) {

		switch (slotN) {
		case InputEvent.ITEM_1_USE:
			this.useItemSlot(1);
			break;
		case InputEvent.ITEM_2_USE:
			this.useItemSlot(2);
			break;
		case InputEvent.ITEM_3_USE:
			this.useItemSlot(3);
			break;
		case InputEvent.ITEM_4_USE:
			this.useItemSlot(4);
			break;
		case InputEvent.ITEM_5_USE:
			this.useItemSlot(5);
			break;
		default:
			// Unknown slot.
			console.warn('Unknown slot to cast.');
			break;
		}
	}
		
		/**
		 * This event will get triggered if the engine has decided to let an
		 * item be cast. It is now the inventories responsibility to give the
		 * server the item cast command and if successful remove the item from
		 * the inventory.
		 * 
		 * @param {Object}
		 *            data - Containt the item to be cast as well as the map
		 *            coordinates e.g.: {item: ITEM, cords: {x: X, y: Y}}.
		 */
		_handlerDoCast(_, data) {
			// We have now all data in place to create a server message to use
			// this item.
			var msg = new Message.InventoryItemCast(data.item.playerItemId(), this._selectedBestia.playerBestiaId(),
					data.cords.x, data.cords.y);

			this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
		}

		/**
		 * Handler if the server advises to re-render the inventory.
		 */
		_handleList(_, data) {
			var newItems = [];

			this.allItems.removeAll();
			this.dropAmount(0);

			data.pis.forEach(function(val) {
				var item = new ItemViewModel(val, this._urlHelper);
				newItems.push(item);
				this.allItems.push(item);
			}, this);

			// Update the weight display.
			this.maxWeight(data.mw);

			this._translateItems(newItems, function() {
				// Flag that all items are sucessfully loaded.
				this._setupItemBindings();
			}.bind(this));
		}

		/**
		 * Updates the item via an update message from the server.
		 */
		_handleUpdate(_, data) {

			var newItems = [];
			var announceItems = [];

			data.pis.forEach(function(val) {

				var item = this._findItem(val.i.id);

				if (val.a > 0) {
					// Item is added to the inventory.
					if (item === null) {
						// Add the item to the inventory.
						var newItem = new ItemViewModel(val);
						this.allItems.push(newItem);
						newItems.push(newItem);
					} else {
						// Otherwise update it.
						item.amount(item.amount() + val.a);
					}

					announceItems.push({
						item : item,
						amount : val.a
					});
				} else {
					// Item must be removed.
					if (item !== null) {
						// Amount is negative. so add.
						var newAmount = item.amount() + val.a;
						if (newAmount > 0) {
							item.amount(newAmount);
						} else {
							this._removeItem(item.itemId());
						}
					}
				}
			}, this);

			// Bulk translate all new items.
			if (newItems.length > 0) {
				this._translateItems(newItems, function() {
					announceItems.forEach(function(val) {
						// Send notifications for other sub systems.
						this._pubsub.publish(Signal.INVENTORY_ITEM_ADD, val.item, val.amount);
					}, this);
				});
			} else {
				// Just announce. Items already translated.
				announceItems.forEach(function(val) {
					// Send notifications for other sub systems.
					this._pubsub.publish(Signal.INVENTORY_ITEM_ADD, val.item, val.amount);
				}, this);
			}

			// If the amount of the selected item has changed, change the drop
			// amount to the current number if it matches.
			if (this.selectedItem() !== null) {
				if (this.selectedItem().amount() + 1 === this.dropAmount()) {
					this.dropAmount(this.selectedItem().amount());
				}
			}
		}

		/**
		 * Saves bestia reference of the currently selected bestia.
		 * 
		 * @param {Bestia.BestiaViewModel}
		 *            bestia - The newly selected bestia.
		 */
		_handlerBestiaSelected(_, bestia) {
			this.hasLoaded(false);

			this._selectedBestia = bestia;

			// Clear all item shortcuts.
			this.itemSlot1(null);
			this.itemSlot2(null);
			this.itemSlot3(null);
			this.itemSlot4(null);
			this.itemSlot5(null);

			this._setupItemBindings();
		}

		/**
		 * Internal method to translate item names and desciptions. Awaits an
		 * array with item models to translate.
		 * 
		 * @private
		 * @param {Array[Bestia.ItemViewModel]}
		 *            items - Array of item view models to translate.
		 * @param {Function}
		 *            fn - Callback function. Is fired when all items are
		 *            translated.
		 */
		_translateItems(items, fn) {
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

				// Trigger callback.
				if (fn !== undefined) {
					fn();
				}
			});
		}

		/**
		 * The item is cleanly removed and deleted from all binding lists etc.
		 * This method should always be used to completly remove an item from
		 * the inventory.
		 * 
		 * @private
		 * @param itemId
		 */
		_removeItem(item) {
			// Check if it is the selected item.
			if (this.selectedItem().playerItemId() === item.playerItemId()) {
				this.selectedItem(null);
			}

			if (this.itemSlot1.playerItemId() === item.playerItemId()) {
				this.unbindItem(1);
			}
			if (this.itemSlot2.playerItemId() === item.playerItemId()) {
				this.unbindItem(2);
			}
			if (this.itemSlot3.playerItemId() === item.playerItemId()) {
				this.unbindItem(3);
			}
			if (this.itemSlot4.playerItemId() === item.playerItemId()) {
				this.unbindItem(4);
			}
			if (this.itemSlot5.playerItemId() === item.playerItemId()) {
				this.unbindItem(5);
			}

			// Remove the item.
			this.allItems.remove(item);
		}

		/**
		 * Looks for the item in the current items array. If it is found the
		 * {Bestia.Inventory.ItemViewModel} is returned. Null otherwise.
		 * 
		 * @private
		 * @param itemId
		 * @returns The {Bestia.Inventory.ItemViewModel} if found or null
		 *          otherwise.
		 */
		_findItem(itemId) {
			var items = this.allItems();
			for (var i = 0; i < items.length; i++) {
				if (items[i].itemId() == itemId) {
					return items[i];
				}
			}
			return null;
		}

		/**
		 * Shows the inventory window.
		 */
		show() {
			this.showWindow(true);
		}

		/**
		 * Hides the inventory window.
		 */
		close() {
			this.showWindow(false);
		}

		/**
		 * Sends the item bindings of the currently selected bestia to the
		 * server.
		 */
		saveItemBindings() {
			var piId1 = this.itemSlot1() ? this.itemSlot1().playerItemId() : null;
			var piId2 = this.itemSlot2() ? this.itemSlot2().playerItemId() : null;
			var piId3 = this.itemSlot3() ? this.itemSlot3().playerItemId() : null;
			var piId4 = this.itemSlot4() ? this.itemSlot4().playerItemId() : null;
			var piId5 = this.itemSlot5() ? this.itemSlot5().playerItemId() : null;
			var bestiaId = this._selectedBestia.playerBestiaId();
			var msg = new Message.ItemSet(bestiaId, piId1, piId2, piId3, piId4, piId5);
			this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
		}

		/**
		 * This function will handle usages of item shortcut slots. Either if
		 * they are clicked directly or if they are invoked via an key press
		 * event. The function will decide if the item has rather to be used
		 * (usable) or if it will get casted (castable). It is also responsible
		 * for making visual fx like flashing the shortcut binding etc.
		 * 
		 * @param slotN
		 *            Number of the slot to be used.
		 */
		useItemSlot(slotN) {
			var item = null;
			switch (slotN) {
			case 1:
				item = this.itemSlot1();
				break;
			case 2:
				item = this.itemSlot2();
				break;
			case 3:
				item = this.itemSlot3();
				break;
			case 4:
				item = this.itemSlot4();
				break;
			case 5:
				item = this.itemSlot5();
				break;
			}

			// Was slot empty?
			if (item === null) {
				return;
			}

			this.useItem(item);
		}

		/**
		 * Setup the item bindings with the proper items. We need to derefer
		 * this call until the bestia is selected AND the items have been
		 * loaded.
		 */
		_setupItemBindings() {
			if (this.hasLoaded()) {
				return;
			}

			var bestia = this._selectedBestia;
			// If we still have no bestia selected via a server message we will
			// stop
			// here and wait until this has happened. The method will then be
			// called
			// again.
			if (bestia === null) {
				return;
			}

			var item = null;

			// Set the item shortcuts by the ones of the newly selected bestia.
			// But these items are not the same instance then the ones from the
			// inventory. We must replace them with the inventory instances.
			if (bestia.item1() !== null) {
				item = this._findItem(bestia.item1().itemId());
				this.itemSlot1(item);
			}
			if (bestia.item2() !== null) {
				item = this._findItem(bestia.item2().itemId());
				this.itemSlot2(item);
			}
			if (bestia.item3() !== null) {
				item = this._findItem(bestia.item3().itemId());
				this.itemSlot3(item);
			}
			if (bestia.item4() !== null) {
				item = this._findItem(bestia.item4().itemId());
				this.itemSlot4(item);
			}
			if (bestia.item5() !== null) {
				item = this._findItem(bestia.item5().itemId());
				this.itemSlot5(item);
			}

			this.hasLoaded(true);
		}
}