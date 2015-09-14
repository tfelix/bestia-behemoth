/**
 * <p>
 * The Attacks module is able to perform a request to the server in order to
 * query the latest attack informations. It will keep this informations cached
 * until a new bestia is selected and the attack list itself is voided. However
 * the atacks descriptions stays beeing cached in order to reduce network
 * queries for the attacks.
 * </p>
 * <p>
 * The current list is invalidated as soon as a new bestia is selected.
 * </p>
 * 
 * @class Bestia.BestiaAttacks
 * @param {Bestia.PubSub}
 *            pubsub - Handle to the pubsub system.
 */
Bestia.BestiaAttacks = function(pubsub) {

	var self = this;

	/**
	 * Handle to the Pub/Sub system.
	 * 
	 * @private
	 * @property
	 */
	this._pubsub = pubsub;

	/**
	 * Boolean flag if the attacklist is cached and can be displayed. Nice for
	 * layouts.
	 * 
	 * @property
	 */
	this.hasAttacklist = ko.observable(false);

	this.attackList = ko.observableArray();

	/**
	 * Private cache which will hold ALL translated attacks. Resetting the
	 * language will invalidate it.
	 * 
	 * @private
	 * @property
	 */
	this.attackCache = {};

	this.currentLang = '';

	this._translationToken = '';
	this._requestedAttacks = [];

	/**
	 * Invalidates all caches if new language is different then the old one.
	 */
	this.invalidateCacheHandle = function(_, data) {
		if (self.currentLang === data.lang) {
			return;
		}

		self.currentLang = data.lang;

		// Reset all caches.
		self.attackCache = {};
		// Reset attack list.
		self.invalidateListHandle();
	};
	pubsub.subscribe('i18n.lang', this.invalidateCacheHandle);

	/*
	 * this.receivedTranslation = function(_, data) { // First check if this is
	 * the translation we have requested via its // token. if
	 * (self._translationToken !== data.t) { return; }
	 * 
	 * var translatedAttacks = []; // The translation will contain attack names
	 * and descriptions. Fill the // waiting objects. };
	 * path.subscribe('translation.response', this.receivedTranslation);
	 */

	/**
	 * Handles newly arriving list of attacks. It will check if we have a
	 * completly translated list of attacks for the list. If so it will simplay
	 * display it. If not it will fetch the remaining attack translations and
	 * wait for the async handle to return.
	 */
	this.updateHandle = function(_, data) {

		self.attackList.removeAll();
		
		//var untranslatedAttacks = [];

		// TODO For now simplay display the attacks.
		data.atks.forEach(function(val) {
			self.attackList.push(new Bestia.BestiaAttack(val));
		});
		self.hasAttacklist(true);

		/*
		 * for (var i = 0; i < data.is.length; i++) { var attack = data.is[i];
		 * 
		 * if (!(attack.id in self.attackCache)) {
		 * untranslatedAttacks.push(attack); } } // Check if we can simply
		 * display the attacklist. if (untranslatedAttacks.length === 0) { }
		 * else { // Request an translation for the missing attacks from the
		 * server. }
		 */

	};
	pubsub.subscribe('attack.list.response', this.updateHandle);

	/**
	 * Resets the current list if for example a new bestia was selected.
	 */
	this.invalidateListHandle = function() {
		this.hasAttacklist(false);
		this.attackList.removeAll();
	};
	pubsub.subscribe('engine.selectBestia', this.invalidateListHandle);
};

/**
 * Requests the attack list from the server, only if it is not already display.
 * If the caches or not dirty this method call will do nothing and just return.
 */
Bestia.BestiaAttacks.prototype.request = function() {
	if (this.hasAttacklist()) {
		return;
	}

	var msg = new Bestia.Message.AttackListRequest();
	this._pubsub.publish('io.sendMessage', msg);
};

Bestia.BestiaAttack = function(data) {
	this.minLevel = ko.observable(0);
	this.id = ko.observable(0);
	this.atkDbName = ko.observable('');
	this.manaCost = ko.observable(0);
	this.strength = ko.observable(0);
	this.range = ko.observable(0);
	this.basedStatus = ko.observable('');
	this.casttime = ko.observable(0);
	this.cooldown = ko.observable(0);

	// These values must be translated via our translation service.
	this.name = ko.observable('');
	this.desc = ko.observable('');

	if (data !== undefined) {
		this.update(data);
	}
};

Bestia.BestiaAttack.prototype.update = function(data) {
	var atk = data.a;
	this.minLevel(data.mlv);
	this.id(atk.id);
	this.atkDbName(atk.adbn);
	this.strength(atk.str);
	this.element(atk.ele);
	this.manaCost(atk.m);
	this.range(atk.r);
	this.basedStatus(atk.bs);
	this.casttime(atk.ct);
	this.cooldown(atk.cd);
};