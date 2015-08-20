/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * ViewModel of a Bestia.
 * 
 * @class Bestia.BestiaViewModel
 * 
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 * @param {Object}
 *            msg - Optional. Server message object to initialize the model with
 *            values.
 * @constructor
 */
Bestia.BestiaViewModel = function(pubsub, msg) {
	if(!(pubsub instanceof Bestia.PubSub)) {
		throw "Bestia.BestiaViewModel: PubSub must be given."; 
	}
	
	var self = this;

	this._pubsub = pubsub;

	this.playerBestiaId = ko.observable();
	this.databaseName = ko.observable('');
	this.equip = [];
	this.location = ko.observable('');
	this.posX = ko.observable(0);
	this.posY = ko.observable(0);
	this.saveLocation = ko.observable();
	this.customName = ko.observable('');
	this.sprite = ko.observable('');
	this.statusEffects = [];
	this.iconUrl = ko.pureComputed(function() {
		return Bestia.Urls.assetsMobIcon + self.databaseName() + '.png';
	});
	this.level = ko.observable(1);
	this.slot = ko.observable();
	this.statusPoints = new Bestia.StatusPointViewModel();

	if (msg !== undefined) {
		this.update(msg);
		this.statusPoints.update(msg.sp);
	}

	/**
	 * This method selects this bestia as active. All server updates will then
	 * forwarded to this bestia.
	 * 
	 * @method Bestia.BestiaViewModel#selectBestia
	 */
	this.selectBestia = function() {
		var activeMsg = new Bestia.Message.BestiaActivate(this.playerBestiaId());
		this._pubsub.publish('io.sendMessage', activeMsg);
	};
};

/**
 * Updates the model with new data from the server.
 * 
 * @method Bestia.BestiaViewModel#update
 * @param {Object}
 *            msg - Message object from the server.
 */
Bestia.BestiaViewModel.prototype.update = function(msg) {
	this.playerBestiaId(msg.id);
	this.location(msg.cl.mdbn);
	this.posX(msg.cl.x);
	this.posY(msg.cl.y);
	this.saveLocation(msg.sl.mdbn);
	this.customName(msg.cn);
	this.level(msg.lv);
	this.databaseName(msg.b.bdbn);
	this.sprite(msg.b.s);
	// this.statusEffects = [];
	// this.slot(msg.sl);
	this.statusPoints.update(msg.sp);
};