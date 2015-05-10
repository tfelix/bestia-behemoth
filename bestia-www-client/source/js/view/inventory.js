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
Bestia.Inventory.EquipItemInfoViewModel = function(msg) {
	var self = this;
	self._upgrade_lv = 0;
	self.upgradeLevel = ko.pureComputed(function() {
		if (self._upgrade_lv > 0) {
			return '+' + self._upgrade_lv;
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
Bestia.Inventory.EquipItemInfoViewModel.prototype.update = function(msg) {
	this.bBroken(1 === msg.bb);
	if (msg.f === null) {
		this._forger.playerId(0);
		this._forger.name('');
	} else {
		this._forger.playerId(msg.f.id);
		this._forger.name(msg.f.n);
	}
	this._upgrade_lv = msg.ulv;
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
Bestia.Inventory.ItemViewModel = function(net, msg) {
	if(!(net instanceof Bestia.Net)) {
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

	self.equipItemInfo = new Bestia.Inventory.EquipItemInfoViewModel();
	self.bQuestItem = ko.observable(true);
	self.bSoulbound = ko.observable(true);
	self.amount = ko.observable(0);
	self.name = ko.observable('');
	self.description = ko.observable('Lorem ipsum.');

	if (msg !== undefined) {
		self.update(msg);
	}
};

Bestia.Inventory.ItemViewModel.prototype.update = function(msg) {
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