import Signal from '../io/Signal.js';

/**
 * This manager is for controlling the user keyboard input. It will react upon
 * direct button presses and upon touch or click events onto the buttons.
 * Actions can be defined (basically what should happen one the user presses a
 * certain key). The class uses an internal PubSub class to trigger the actions
 * defined for a certain key.
 */
export default class InputManager {
	constructor(pubsub) {
		this._pubsub = pubsub;

		

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
}