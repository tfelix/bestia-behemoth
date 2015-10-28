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
Bestia.BestiaAttacks = function(pubsub, i18n) {

	var self = this;

	/**
	 * Handle to the Pub/Sub system.
	 * 
	 * @private
	 * @property
	 */
	this._pubsub = pubsub;

	/**
	 * Private handle to the i18n module to use it for translations.
	 * 
	 * @private
	 * @property {Bestia.I18n}
	 */
	this._i18n = i18n;

	/**
	 * Boolean flag if the attack list is cached and can be displayed. Nice for
	 * layouts.
	 * 
	 * @property
	 */
	this.hasAttacks = ko.observable(false);

	/**
	 * Holds the array with all known attacks currently requested from the
	 * server.
	 * 
	 * @property Array[{Bestia.BestiaAttack}]
	 */
	this.attacks = ko.observableArray();

	/**
	 * Handles newly arriving list of attacks. It will check if we have a
	 * completly translated list of attacks for the list. If so it will simply
	 * display it. If not it will fetch the remaining attack translations and
	 * wait for the async handle to return.
	 */
	this.updateHandle = function(_, data) {

		self.attacks.removeAll();

		var attackTranslationList = [];

		data.atks.forEach(function(val) {
			var attack = new Bestia.BestiaAttack(val);
			self.attacks.push(attack);
			attackTranslationList.push(attack);
		});
		self.hasAttacks(true);

		var buildTranslationKey = function(atk) {
			return 'attack.' + atk.attackDatabaseName();
		};
		
		var buildTranslationKeyDesc = function(atk) {
			return 'attack.' + atk.attackDatabaseName() + '_desc';
		};

		var i18nKeys = attackTranslationList.map(buildTranslationKey);
		// Add the keys with for the description.
		i18nKeys = i18nKeys.concat(attackTranslationList.map(buildTranslationKeyDesc));

		self._i18n.t(i18nKeys, function(t) {
			attackTranslationList.forEach(function(val) {
				val.name(t(buildTranslationKey(val)));
				val.description(t(buildTranslationKeyDesc(val)));
			});
		});

	};
	pubsub.subscribe('attack.list.response', this.updateHandle);

	/**
	 * Resets the current list if for example a new bestia was selected.
	 */
	this.invalidateListHandle = function() {
		this.hasAttacks(false);
		this.attacks.removeAll();
	};
	pubsub.subscribe('engine.selectBestia', this.invalidateListHandle);
	/**
	 * Invalidates all caches if new language is different then the old one.
	 */
	pubsub.subscribe('i18n.lang', this.invalidateListHandle);
};

/**
 * Requests the attack list from the server, only if it is not already display.
 * If the caches or not dirty this method call will do nothing and just return.
 */
Bestia.BestiaAttacks.prototype.request = function() {
	if (this.hasAttacks()) {
		return;
	}

	var msg = new Bestia.Message.AttackListRequest();
	this._pubsub.publish('io.sendMessage', msg);
};

Bestia.BestiaAttack = function(data) {
	this.minLevel = ko.observable(0);
	this.id = ko.observable(0);
	this.attackDatabaseName = ko.observable('');
	this.manaCost = ko.observable(0);
	this.strength = ko.observable(0);
	this.range = ko.observable(0);
	this.basedStatus = ko.observable('');
	this.element = ko.observable('');
	this.casttime = ko.observable(0);
	this.cooldown = ko.observable(0);

	// These values must be translated via our translation service.
	this.name = ko.observable('');
	this.description = ko.observable('');

	if (data !== undefined) {
		this.update(data);
	}
};

Bestia.BestiaAttack.prototype.update = function(data) {
	var atk = data.a;
	this.minLevel(data.mlv || 0);
	this.id(atk.id);
	this.attackDatabaseName(atk.adbn);
	this.strength(atk.str);
	this.element(atk.ele);
	this.manaCost(atk.m);
	this.range(atk.r);
	this.basedStatus(atk.bs);
	this.casttime(atk.ct);
	this.cooldown(atk.cd);
};