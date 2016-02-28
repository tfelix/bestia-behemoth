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
 * @param {Object}
 *            statusPoints - StatusPoints data object from the server to hold
 *            information about the current stats.
 * @constructor
 */
Bestia.BestiaViewModel = function(pubsub, msg, statusPoints, urlHelper) {
	if (!(pubsub instanceof Bestia.PubSub)) {
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
		return urlHelper.getMobIconUrl(self.databaseName());
	});
	this.level = ko.observable(1);
	this.slot = ko.observable();
	this.statusPoints = new Bestia.StatusPointViewModel();

	this.attack1 = ko.observable(null);
	this.attack2 = ko.observable(null);
	this.attack3 = ko.observable(null);
	this.attack4 = ko.observable(null);
	this.attack5 = ko.observable(null);

	this.item1 = ko.observable(null);
	this.item2 = ko.observable(null);
	this.item3 = ko.observable(null);
	this.item4 = ko.observable(null);
	this.item5 = ko.observable(null);

	this.update(msg, statusPoints);

	/**
	 * This method selects this bestia as active. All server updates will then
	 * forwarded to this bestia.
	 * 
	 * @method Bestia.BestiaViewModel#selectBestia
	 */
	this.selectBestia = function() {
		var activeMsg = new Bestia.Message.BestiaActivate(this.playerBestiaId());
		this._pubsub.send(activeMsg);
	};
};

/**
 * Updates the model with new data from the server.
 * 
 * @method Bestia.BestiaViewModel#update
 * @param {Object}
 *            msg - Message object from the server.
 */
Bestia.BestiaViewModel.prototype.update = function(msg, sp) {
	if (msg !== undefined) {
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

		// Update the attacks.
		if (msg.atk1) {
			this.attack1(new Bestia.BestiaAttack(msg.atk1));
		}

		if (msg.atk2) {
			this.attack2(new Bestia.BestiaAttack(msg.atk2));
		}

		if (msg.atk3) {
			this.attack3(new Bestia.BestiaAttack(msg.atk3));
		}

		if (msg.atk4) {
			this.attack4(new Bestia.BestiaAttack(msg.atk4));
		}

		if (msg.atk5) {
			this.attack5(new Bestia.BestiaAttack(msg.atk5));
		}

		// Update the items.
		if (msg.item1) {
			this.item1(new Bestia.ItemViewModel(msg.item1));
		}

		if (msg.item2) {
			this.item2(new Bestia.ItemViewModel(msg.item2));
		}

		if (msg.item3) {
			this.item3(new Bestia.ItemViewModel(msg.item3));
		}

		if (msg.item4) {
			this.item4(new Bestia.ItemViewModel(msg.item4));
		}

		if (msg.item5) {
			this.item5(new Bestia.ItemViewModel(msg.item5));
		}
	}
	
	if (sp !== undefined) {
		this.statusPoints.update(sp);
	}
};