/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

Bestia.Inventory = Bestia.Inventory || {};

/**
 * Holds the ViewModel for the EquipmentItemInfo.
 * 
 * @class Bestia.Inventory.EquipItemInfoViewModel
 * @constructor
 * @param {Object}
 *            msg - Optional, message used to initialize the view model.
 */
Bestia.EquipItemInfoViewModel = function(msg) {
	var self = this;
	/**
	 * Upgraded item level.
	 * 
	 * @private
	 */
	self._upgradeLv = 0;

	/**
	 * @returns {String}
	 */
	self.upgradeLevel = ko.pureComputed(function() {
		if (self._upgradeLv > 0) {
			return '+' + self._upgradeLv;
		} else {
			return '';
		}
	});
	self._forger = {
		playerId : ko.observable(0),
		name : ko.observable('')
	};
	self.forgerName = self._forger.name;
	self.bBroken = ko.observable(false);

	if (msg !== undefined) {
		self.update(msg);
	}
};

/**
 * Adds a new Frame to this FrameData collection. Typically called by the
 * Animation.Parser and not directly.
 * 
 * @method Bestia.Inventory.EquipItemInfoViewModel#update
 * @param {Object}
 *            msg - Message json object to set new data.
 */
Bestia.EquipItemInfoViewModel.prototype.update = function(msg) {
	this.bBroken(1 === msg.bb);
	if (msg.f === null) {
		this._forger.playerId(0);
		this._forger.name('');
	} else {
		this._forger.playerId(msg.f.id);
		this._forger.name(msg.f.n);
	}
	this._upgradeLv = msg.ulv;
};

/**
 * In this module all methods, views for the display and the user interaction
 * with the inventory will be defined.
 * 
 * @class Bestia.Inventory.ItemViewModel
 * @constructor
 * @param {Bestia.Net}
 *            net - Handle to a Net object which will provide correct resource
 *            URLs to do on demand ajax loading.
 * @param {Object}
 *            msg - Optional, message used to initialize the view model.
 */
Bestia.ItemViewModel = function(net, msg) {
	if (!(net instanceof Bestia.Net)) {
		throw "net is not an instance of Bestia.Net";
	}

	var self = this;

	this._net = net;

	self.itemId = ko.observable(0);
	self.playerItemId = ko.observable(0);
	self._img = '';
	self.imageURL = ko.pureComputed(function() {
		return self._net.getItemImageUrl(self._img);
	});
	self._type = '';

	/**
	 * @property {string} type - Type of this item.
	 */
	self.type = ko.pureComputed(function() {
		switch (self._type) {
		case 0:
			return 'etc';
		case 1:
			return 'usable';
		case 2:
			return 'equip';
		default:
			return 'etc';
		}
	});

	self.equipItemInfo = new Bestia.EquipItemInfoViewModel();
	self.bQuestItem = ko.observable(true);
	self.bSoulbound = ko.observable(true);
	self.amount = ko.observable(0);

	self.name = ko.observable('???');
	self.description = ko.observable('Lorem ipsum.');

	// Async request the correct translation for the items.
	ko.computed(function() {
		$.ajax(self._net.getItemI18NUrl(self.itemId()), {
			success : function(data) {
				self.name(data.name);
				self.description(data.desc);
			}
		});
	});

	if (msg !== undefined) {
		self.update(msg);
	}
};

/**
 * Updates the data of this item with a new JSON message from the server.
 * 
 * @method Bestia.Inventory.ItemViewModel#update
 * @param {Object}
 *            msg - Message json object to set new data.
 */
Bestia.ItemViewModel.prototype.update = function(msg) {
	this.itemId(msg.iid);
	this.playerItemId(msg.pid);
	this._img = msg.img;
	this._type = msg.t;
	this.bQuestItem(msg.bq);
	this.bSoulbound(msg.bs);
	this.amount(msg.a);

	if (msg.eqii !== null) {
		this.equipItemInfo.update(msg.eqii);
	}
};

/**
 * Manages all the displayed items and the user interaction with them.
 * 
 * @class Bestia.Inventory.Inventory
 * @param {Bestia.Net}
 *            net - Network helper class.
 * @constructor
 */
Bestia.Inventory = function(net) {

	var self = this;

	this._net = net;
	this.items = ko.observableArray();

	// Register for messages.
	Bestia.subscribe('inventory.init', function(_, msg) {
		self._onMessageInit(msg);
	});
	Bestia.subscribe('inventory.add', function(_, msg) {
		self._onMessageAdd(msg);
	});
	Bestia.subscribe('inventory.remove', function(_, msg) {
		self._onMessageRemove(msg);
	});
	
	// Set onclick handler.
	$('#show-inventory').click(function(){
		$('#modal-inventory').modal('show');
	});
};

/**
 * Requests a complete dataset of the current inventory from the server.
 * 
 * @method Bestia.Inventory.Inventory#init
 */
Bestia.Inventory.prototype.init = function() {
	Bestia.publish('io.send', new Bestia.Message.InventoryRequest());
};

/**
 * Requests a complete dataset of the current inventory from the server.
 * 
 * @method Bestia.Inventory.Inventory#_onMessageInit
 * @param {Array}
 *            msg - An array with JSON messages for items.
 * @private
 */
Bestia.Inventory.prototype._onMessageInit = function(msg) {
	var self = this;

	// Clear all existing items.
	self.items.removeAll();

	msg.forEach(function(val) {
		self.items.push(new Bestia.ItemViewModel(self._net, val));
	});
};

/**
 * 
 * @paran {Number} itemId - player item id of the item to be found. Returns the
 *        ItemViewModel or null if no item was found.
 * @private
 * @return Bestia.Inventory.ItemViewModel
 */
Bestia.Inventory.prototype._findItem = function(playerItemId) {

	for (var i = 0; i < this.items().length; ++i) {
		var val = this.items()[i];
		if (val.playerItemId() === playerItemId) {
			return val;
		}
	}

	return null;
};

/**
 * Displays the inventory modal dialog.
 */
Bestia.Inventory.prototype.showInventory = function() {
	// TODO gegen ein gespeichertes DOM node austauschen.
	$('#modal-inventory').modal('show');
};

/**
 * Removes an item if the server sends this message.
 */
Bestia.Inventory.prototype._onMessageRemove = function(msg) {
	var item = this._findItem(msg.pid);
	if (item === null) {
		return;
	}

	// Set item amount if bit enough.
	if (item.amount() > msg.a) {
		item.amount(item.amount() - msg.a);
		return;
	}

	// Remove item.
	this.items.remove(item);
};

/**
 * Adds an item to the inventory if the given message is encountered.
 */
Bestia.Inventory.prototype._onMessageAdd = function(msg) {
	var item = this._findItem(msg.pid);
	if (item !== null) {
		// Is it an equip? If so create new item slot.
		if (item.type() == 'equip') {
			this.items.push(new Bestia.ItemViewModel(this._net, msg));
			return;
		}

		item.amount(item.amount() + msg.a);
		return;
	}

	this.items.push(new Bestia.ItemViewModel(this._net, msg));
};
