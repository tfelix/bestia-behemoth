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
/* jshint unused: false */
/* globals interact */
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
	this.isLoaded = ko.observable(false);

	/**
	 * Holds the array with all known attacks currently requested from the
	 * server.
	 * 
	 * @property Array[{Bestia.BestiaAttack}]
	 */
	this.attacks = ko.observableArray();

	/**
	 * Attack slot 1 of the current bestia.
	 * 
	 * @property{Bestia.BestiaAttack}
	 */
	this.attackSlot1 = ko.observable();

	/**
	 * Attack slot 2 of the current bestia.
	 * 
	 * @property{Bestia.BestiaAttack}
	 */
	this.attackSlot2 = ko.observable();

	/**
	 * Attack slot 3 of the current bestia.
	 * 
	 * @property{Bestia.BestiaAttack}
	 */
	this.attackSlot3 = ko.observable();

	/**
	 * Attack slot 4 of the current bestia.
	 * 
	 * @property{Bestia.BestiaAttack}
	 */
	this.attackSlot4 = ko.observable();

	/**
	 * Attack slot 5 of the current bestia.
	 * 
	 * @property {Bestia.BestiaAttack}
	 */
	this.attackSlot5 = ko.observable();

	/**
	 * The attack the user has currently selected.
	 * 
	 * @property {Bestia.Attack}
	 */
	this.selectedAttack = ko.observable(null);

	/**
	 * Flag if the window of the attack management should be shown.
	 */
	this.showWindow = ko.observable(false);

	/**
	 * Handles newly arriving list of attacks. It will check if we have a
	 * completly translated list of attacks for the list. If so it will simply
	 * display it. If not it will fetch the remaining attack translations and
	 * wait for the async handle to return.
	 */
	var updateHandle = function(_, data) {

		self.attacks.removeAll();

		var attackTranslationList = [];

		data.atks.forEach(function(val) {
			var attack = new Bestia.BestiaAttack(val);
			self.attacks.push(attack);
			attackTranslationList.push(attack);
		});
		self.isLoaded(true);

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

	pubsub.subscribe('attack.list.response', updateHandle);

	/**
	 * Resets the current list if for example a new bestia was selected or the
	 * language was set.
	 */
	var invalidateListHandle = function() {
		this.isLoaded(false);
		this.attacks.removeAll();
	};

	pubsub.subscribe('client.selectBestia', invalidateListHandle);
	pubsub.subscribe('i18n.lang', invalidateListHandle);

	/**
	 * Selects the attack.
	 * 
	 * @param attack
	 */
	this.selectAttack = function(attack) {
		self.selectedAttack(attack);
	};
	
	/**
	 * Binds the currently selected attack on a given slot.
	 */
	this.useSelectedAttack = function(slot) {
		
		if(!self.selectedAttack()) {
			return;
		}
		
		switch (slot) {
		case 1:
			self.attackSlot1(self.selectedAttack());
			break;
		case 2:
			self.attackSlot2(self.selectedAttack());
			break;
		case 3:
			self.attackSlot3(self.selectedAttack());
			break;
		case 4:
			self.attackSlot4(self.selectedAttack());
			break;
		case 5:
			self.attackSlot5(self.selectedAttack());
			break;
		default:
			throw "Slotnumber must be between 1 and 5.";
		}
		self.saveAttacks();
	};

	/**
	 * Removes an attack from a given slot. The slot will be set to null and the
	 * change will be send to the server.
	 * 
	 * @param slot
	 *            Number of the slot. Between 1 and 4.
	 */
	this.removeAttack = function(slot) {

		switch (slot) {
		case 1:
			this.attackSlot1(null);
			break;
		case 2:
			this.attackSlot2(null);
			break;
		case 3:
			this.attackSlot3(null);
			break;
		case 4:
			this.attackSlot4(null);
			break;
		case 5:
			this.attackSlot5(null);
			break;
		default:
			throw "Slotnumber must be between 1 and 4.";
		}
		self.saveAttacks();
	};
};

/**
 * Requests the attack list from the server, only if it is not already display.
 * If the caches or not dirty this method call will do nothing and just return.
 */
Bestia.BestiaAttacks.prototype.request = function() {
	if (this.isLoaded()) {
		return;
	}

	var msg = new Bestia.Message.AttackListRequest();
	this._pubsub.publish('io.sendMessage', msg);
};

/**
 * Attacks can only occupy one slot. If the attack was set on another spot
 * nothing will be done.
 * 
 * @param {Number}
 *            slot - Number of the slot to use. Must be between 1 and 5
 *            (inclusive).
 * @param attackId -
 *            The id of the attack to be used.
 */
Bestia.BestiaAttacks.prototype.setAttack = function(slot, attackId) {
	if (this.isLoaded()) {
		return;
	}

	// Check if the bestia can learn this attack.
	var learnedAtk = this.attacks().filter(function(val) {
		return val.id() === attackId;
	});

	if (learnedAtk.length != 1) {
		throw "Attack was not known.";
	}

	var atk = learnedAtk[0];

	switch (slot) {
	case 1:
		this.attackSlot1(atk);
		break;
	case 2:
		this.attackSlot2(atk);
		break;
	case 3:
		this.attackSlot3(atk);
		break;
	case 4:
		this.attackSlot4(atk);
		break;
	case 5:
		this.attackSlot5(atk);
		break;
	default:
		throw "Slot must be between 1 and 5.";
	}

	// Delete the same attack from all other slots.
	for (var i = 1; i <= 5; i++) {

		if (i === slot) {
			continue;
		}

		switch (i) {
		case 1:
			if (this.attackSlot1().id() === atk.id()) {
				this.attackSlot1(undefined);
			}
			break;
		case 2:
			if (this.attackSlot2().id() === atk.id()) {
				this.attackSlot2(undefined);
			}
			break;
		case 3:
			if (this.attackSlot3().id() === atk.id()) {
				this.attackSlot3(undefined);
			}
			break;
		case 4:
			if (this.attackSlot4().id() === atk.id()) {
				this.attackSlot4(undefined);
			}
			break;
		case 5:
			if (this.attackSlot5().id() === atk.id()) {
				this.attackSlot5(undefined);
			}
			break;
		}
	}

	// Save the new attacks.
	this.saveAttacks();
};

/**
 * Function handler which sets the show flag to false in order to hide the
 * window.
 */
Bestia.BestiaAttacks.prototype.close = function() {
	this.showWindow(false);
};

/**
 * Sends the new attacks inside the slots to the server where it is persisted to
 * the currently selected bestia.
 */
Bestia.BestiaAttacks.prototype.saveAttacks = function() {
	console.log("TODO: SENDEN");
};
