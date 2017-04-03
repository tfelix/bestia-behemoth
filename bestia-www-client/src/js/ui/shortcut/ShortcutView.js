import ko from 'knockout';

/**
 * This class manages the shortcuts of the game for the player. It listens to
 * keyboard inputs and if a apropriate key was pressed it will trigger the
 * associated action with the key.
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

	}

	/**
	 * Saves the current configuration of the shortcut view into a object which can be persisted.
	 * @return {object} Returns a descriptive object which can be used to persist this shortcut view.
	 */
	save() {
		return {};
	}
}