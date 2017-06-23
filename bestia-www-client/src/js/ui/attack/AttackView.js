import Signal from '../../io/Signal';
import ko from 'knockout';
import Message from '../../io/messages/Message';
import Attack from './Attack';
import MID from '../../io/messages/MID';

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
 * @class AttackView
 */
export default class AttackView {
	
	/**
	 * @constructs AttackView
	 * @param {*} pubsub 
	 * @param {*} i18n 
	 */
	constructor(pubsub, i18n) {

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
		 * Boolean flag if the attack list is cached and ready to be displayed. Nice
		 * for layouts.
		 * 
		 * @private
		 * @property
		 */
		this._isLoaded = false;

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
		this.show = ko.observable(false);

		/**
		 * Holds the reference to the currently active bestia. We need this in order
		 * to obtain its id for the send out message.
		 * 
		 * @private
		 * @property
		 */
		this._selectedBestia = null;

		/**
		 * Selects the given attack.
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

			if (!self.selectedAttack()) {
				return;
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
			
			
			self.saveAttacks();
		};
		
		// Setup the subscribers.
		pubsub.subscribe(MID.ATTACK_LIST_RESPONSE, this._updateHandle.bind(this));
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._invalidateListHandle.bind(this));
		//pubsub.subscribe(Signal.I18N_LANG_CHANGED, this._invalidateListHandle.bind(this));
		
		this.show.subscribe(function(newValue){
			if(!this._isLoaded && newValue === true) {
				this.request();
			}
		}, this);
	}
	
	
	
	_invalidateListHandle(_, selectedBestia) {
		this._isLoaded = false;
		this.attacks.removeAll();

		// Set reference to selected bestia.
		this._selectedBestia = selectedBestia;

		// Prepare the attacks for translation.
		var translateAtks = [];
		translateAtks.push(selectedBestia.attack1());
		translateAtks.push(selectedBestia.attack2());
		translateAtks.push(selectedBestia.attack3());
		translateAtks.push(selectedBestia.attack4());
		translateAtks.push(selectedBestia.attack5());

		translateAtks = translateAtks.filter(function(x) {
			return x !== null;
		});
		this._translateAttacks(translateAtks);

		// Reset the attack slots to the attacks of the bestia.
		this.attackSlot1(selectedBestia.attack1());
		this.attackSlot2(selectedBestia.attack2());
		this.attackSlot3(selectedBestia.attack3());
		this.attackSlot4(selectedBestia.attack4());
		this.attackSlot5(selectedBestia.attack5());
	}
	
	
	
	/**
	 * Requests the attack list from the server, only if it is not already display.
	 * If the caches or not dirty this method call will do nothing and just return.
	 */
	request() {
		if (this._isLoaded) {
			return;
		}

		var msg = new Message.AttackListRequest();
		this._pubsub.send(msg);
	}

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
	setAttack(slot, attackId) {
		if (this.isLoaded()) {
			return;
		}

		// Check if the bestia can learn this attack.
		var learnedAtk = this.attacks().filter(function(val) {
			return val.id() === attackId;
		});

		if (learnedAtk.length != 1) {
			throw 'Attack was not known.';
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
			throw 'Slot must be between 1 and 5.';
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
	}
	
	/**
	 * Handles newly arriving list of attacks. It will check if we have a
	 * completly translated list of attacks for the list. If so it will simply
	 * display it. If not it will fetch the remaining attack translations and
	 * wait for the async handle to return.
	 */
	_updateHandle(_, data) {

		this.attacks.removeAll();

		var attackTranslationList = [];

		data.atks.forEach(function(val) {
			var attack = new Attack(val);
			this.attacks.push(attack);
			attackTranslationList.push(attack);
		}, this);
		this.isLoaded(true);

		this._translateAttacks(attackTranslationList);
	}

	/**
	 * Function handler which sets the show flag to false in order to hide the
	 * window.
	 */
	close() {
		this.show(false);
	}

	/**
	 * Internal helper function to translate attacks.
	 * 
	 * @param {Array}
	 *            attacks - Array of attacks to translate.
	 */
	_translateAttacks(attacks) {
		var buildTranslationKey = function(atk) {
			return 'attack.' + atk.attackDatabaseName();
		};

		var buildTranslationKeyDesc = function(atk) {
			return 'attack.' + atk.attackDatabaseName() + '_desc';
		};

		/*
		var i18nKeys = attacks.map(buildTranslationKey);
		// Add the keys with for the description.
		i18nKeys = i18nKeys.concat(attacks.map(buildTranslationKeyDesc));

		this._i18n.t(i18nKeys, function(t) {
			attacks.forEach(function(val) {
				val.name(t(buildTranslationKey(val)));
				val.description(t(buildTranslationKeyDesc(val)));
			});
		});*/
	}

	/**
	 * Sends the new attacks inside the slots to the server where it is persisted to
	 * the currently selected bestia.
	 */
	saveAttacks() {
		var atk1 = this.attackSlot1() ? this.attackSlot1().id() : null;
		var atk2 = this.attackSlot2() ? this.attackSlot2().id() : null;
		var atk3 = this.attackSlot3() ? this.attackSlot3().id() : null;
		var atk4 = this.attackSlot4() ? this.attackSlot4().id() : null;
		var atk5 = this.attackSlot5() ? this.attackSlot5().id() : null;
		var bestiaId = this._selectedBestia.playerBestiaId();
		var msg = new Message.AttackSet(bestiaId, atk1, atk2, atk3, atk4, atk5);
		this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
	}
}
