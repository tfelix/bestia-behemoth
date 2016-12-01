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

		/**
		 * Hash which holds the actions of this manager.
		 */
		this._actions = {};
		
		this.isActive = ko.observable(true);
		

		// Subscribe to keypress events.
		$(document).on("keydown", this._handleInput.bind(this));

		this._pubsub.subscribe(Signal.SET_ACTION, this._handleAction.bind(this));
		this._pubsub.subscribe(Signal.INPUT_LISTEN, this._handleListenControl.bind(this));
	}
	
	/**
	 * This is invoced if we need to handle a certain action by the user.
	 */
	_handleAction(_, msg) {
		
	}
	
	_handleListenControl(_, flag) {
		this._listen = flag;
	}
	
	/**
	 * Handles the keypress event of the user. It will look if there is an
	 * apropriate action for this event.
	 */
	_handleInput(input) {
		if (!this.isActive()) {
			return;
		}

		let keyCode = input.keyCode;

		if(!this._actions.hasOwnProperty(keyCode)) {
			return;
		}
		
		// Execute the action set on this key code.
		this._actions[keyCode].execute();
	}
}