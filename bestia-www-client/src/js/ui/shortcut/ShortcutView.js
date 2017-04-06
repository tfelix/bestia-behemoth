import Shortcut from './Shortcut';
import Signal from '../../io/Signal';
import ko from 'knockout';

/**
 * This class manages the shortcuts of the game for the player. It listens to
 * keyboard inputs and if a apropriate key was pressed it will trigger the
 * associated action with the key.
 * 
 * @class
 */
export default class ShortcutView {

	/**
	 * Ctor.
	 * @constructor
	 * @param {module:/util/PubSub.PubSub} pubsub - Publish subscriber object.
	 * @param {number} rows - The number of shortcut rows this view should have.
	 * @param {number} cols - The number of cols this view should have.	 
	 */
	constructor(pubsub, rows, cols) {
		if (!pubsub) {
			throw 'Pubsub can not be empty.';
		}

		rows = rows || 2;
		cols = cols || 5;

		this._pubsub = pubsub;

		// Prepare the rows.
		for (let i = 0; i < 5; i++) {
			let r = ko.observableArray();
			for (let j = 0; j < 5; j++) {
				r().push(new Shortcut());
			}
			this.rows.push({ row:  r});
		}

		// Activate and deactivate the shortcuts system.
		this._pubsub.subscribe(Signal.INPUT_LISTEN, this._handleDisable, this);

		// On bestia changes re-request the shortcut list.
		this._pubsub.subscribe(Signal.BESTIA_SELECTED, this._requestShortcuts, this);
	}

	/**
	 * Requests the new shortcuts from the server.
	 * @private
	 */
	_requestShortcuts() {

	}

	/**
	 * Disables the listenting to inputs if another source is active.
	 * @private
	 */
	_handleDisable(_, isActive) {
		if (isActive) {
			// Enable the shortcut view again.
		} else {
			// Disable the shortcut view keypress listenting.
		}
	}

	/**
	 * Handles a keypress of a user.
	 * @private
	 */
	_handleKeypress(e) {
		let keyCode = (typeof e.which == "number") ? e.which : e.keyCode;
		// Search if a binding is set to this keycode.
		for (let i = 0; i < this.rows().length; i++) {
			for (let j = 0; j < this.rows()[i].length; j++) {
				this.rows()[i].row()[j].trigger(keyCode, this._pubsub);
			}
		}
	}

	/*
	 * Saves the current configuration of the shortcut view into a object which can be persisted.
	 * @return {object} Returns a descriptive object which can be used to persist this shortcut view.
	 */
	save() {
		return {};
	}
}