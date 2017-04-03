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

		this._pubsub = pubsub;

		this.rows = ko.observableArray();

		// Prepare the rows.
		for (let i = 0; i < rows; i++) {
			let r = array();
			for (let j = 0; j < rows; j++) {
				r.push(new Shortcut());
			}
			this.rows.push({row: ko.observableArray(r)});
		}

		this._pubsub.subscribe(Signal.INPUT_LISTEN, this._handleDisable, this);
	}

	/**
	 * Disables the listenting to inputs if another source is active.
	 * @private
	 */
	_handleDisable(_, isActive) {
		if(isActive) {
			// Enable the shortcut view again.
		} else {
			// Disable the shortcut view keypress listenting.
		}

	/*
	 * Saves the current configuration of the shortcut view into a object which can be persisted.
	 * @return {object} Returns a descriptive object which can be used to persist this shortcut view.
	 */
	save() {
		return {};
	}
}