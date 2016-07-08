import Signal from '../io/Signal.js';

/**
 * 
 * @constant
 */
var InputEvent = {
	ATTACK_1_USE : 101,
	ATTACK_2_USE : 102,
	ATTACK_3_USE : 103,
	ATTACK_4_USE : 104,
	ATTACK_5_USE : 105,
	ITEM_1_USE : 201,
	ITEM_2_USE : 202,
	ITEM_3_USE : 203,
	ITEM_4_USE : 204,
	ITEM_5_USE : 205
};

/**
 * The keyboard receiver takes keycodes send via a keyboard, translates them to
 * Bestia.Input.Actions and sends them out via a pubsub event to all interested
 * parties.
 * 
 * @class
 * @constructor
 */
class KeyboardListener {
	constructor(pubsub) {
		this._pubsub = pubsub;

		this._listen = true;

		// Generate the model.
		this._model = [ {
			keyCode : 49,
			category : 'item',
			event : InputEvent.ITEM_1_USE
		}, {
			keyCode : 50,
			category : 'item',
			event : InputEvent.ITEM_2_USE
		}, {
			keyCode : 51,
			category : 'item',
			event : InputEvent.ITEM_3_USE
		}, {
			keyCode : 52,
			category : 'item',
			event : InputEvent.ITEM_4_USE
		}, {
			keyCode : 53,
			category : 'item',
			event : InputEvent.ITEM_5_USE
		}, {
			keyCode : 81,
			category : 'attack',
			event : InputEvent.ATTACK_1_USE
		}, {
			keyCode : 87,
			category : 'attack',
			event : InputEvent.ATTACK_2_USE
		}, {
			keyCode : 69,
			category : 'attack',
			event : InputEvent.ATTACK_3_USE
		}, {
			keyCode : 82,
			category : 'attack',
			event : InputEvent.ATTACK_4_USE
		}, {
			keyCode : 84,
			category : 'attack',
			event : InputEvent.ATTACK_5_USE
		} ];

		// Subscribe to keypress events.
		$(document).on("keydown", this._handleInput.bind(this));

		this._pubsub.subscribe(Signal.INPUT_LISTEN, this._handleListenControl.bind(this));
	}
	
	_handleListenControl(_, flag) {
		this._listen = flag;
	}
	
	_handleInput(input) {
		if (!this._listen) {
			return;
		}

		var event = null;
		var topic = '';

		// Identify keypress and see if its an registered command.
		for (var i = 0; i < this._model.length; i++) {
			if (this._model[i].keyCode == input.keyCode) {
				event = this._model[i].event;

				switch (this._model[i].category) {
				case 'attack':
					topic = Signal.INPUT_USE_ATTACK;
					break;
				case 'item':
					topic = Signal.INPUT_USE_ITEM;
					break;
				}
			}
		}
		
		if(event == null) {
			return;
		}

		// If so send the appropriate event.
		this._pubsub.publish(topic, event);
	}
	
	/**
	 * In order to replace a receiver, remove() must be called. The call will
	 * unsubscribe him from keyboard events and it will stop to propagate events
	 * into the bestia system for pressed keys.
	 * 
	 * @method Bestia.Input.KeyboardReceiver#remove
	 */
	listen(flag) {
		this._listen = flag;
	}

	/**
	 * In order to replace a receiver, remove() must be called. The call will
	 * unsubscribe him from keyboard events and it will stop to propagate events
	 * into the bestia system for pressed keys.
	 * 
	 * @method Bestia.Input.KeyboardReceiver#remove
	 */
	remove() {
		$(document).off("keydown", this._handler);
	}
}

export {InputEvent, KeyboardListener};