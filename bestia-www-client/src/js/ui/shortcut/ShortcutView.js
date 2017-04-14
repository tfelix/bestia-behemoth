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

		this._rows = rows || 2;
		this._cols = cols || 5;

		this._pubsub = pubsub;

		this.rows = ko.observableArray();

		// Prepare the rows.
		for (let i = 0; i < 5; i++) {
			let r = ko.observableArray();
			for (let j = 0; j < 5; j++) {
				r().push(new Shortcut());
			}
			this.rows.push({ row: r });
		}

		// Activate and deactivate the shortcuts system.
		this._pubsub.subscribe(Signal.INPUT_LISTEN, this._handleDisable, this);

		// On bestia changes re-request the shortcut list.
		this._pubsub.subscribe(Signal.BESTIA_SELECTED, this._requestShortcuts, this);
	}

	/**
	 * Sends a list with all the slots to the caller. 
	 * There will be a callback code attached to the request to it can be identified by the source.
	 * 
	 * @param {string} _ - Name of the published topic.
	 * @param {number} reqCode - Should be a number which will be attached by the send data request_code. 
	 */
	_requestSlots(_, reqCode) {

		let rowsCount = this.rows().length;
		let colsCount = (this.rows().length !== 0) ? 0 : this.rows()[i].row().length;

		let data = { request_code: reqCode, rows: rowsCount, cols: colsCount, slots: [] };

		// Prepare the rows.
		for (let i = 0; i < this._getSlotCount(); i++) {
			let shortcut = this._getShortcutBySlotNum(i);
			data.slots.push(shortcut);
		}

		this._pubsub.publish(Signal.SHORTCUT_REQ_SLOTS, data);
	}

	/**
	 * Returns the total number of slots in this shortcut view.
	 * 
	 * @private
	 * @return {number} Total number of item slots in this shortcut.
	 */
	_getSlotCount() {
		return this._cols * this._rows;
	}

	/**
	 * Returns the Shortcut by the given slot number or null if it does not exist.
	 * 
	 * @private
	 * @param {number} slotNum 
	 */
	_getShortcutBySlotNum(slotNum) {
		if (slotNum < 0 || slotNum > this._getSlotCount()) {
			return null;
		}

		let rowNum = Math.floor(slotNum / this._cols);
		let colNum = slotNum % this._rows;
		return this._rows()[rowNum].row()[colNum];
	}

	/**
	 * Requests the shortcuts from the server for the current active entity/bestia.
	 * 
	 * @private
	 */
	_requestShortcuts() {
		throw 'Not implemented';
	}

	/**
	 * Disables the listenting to inputs if another source is active.
	 * @private
	 */
	_handleDisable(_, isActive) {
		if (isActive) {
			// Enable the shortcut view again.
			// Need to save the callback to remove it later on.
			this._curryCallback = this._handleKeypress.bind(this);
			document.addEventListener('keypress', this._curryCallback);
		} else {
			// Disable the shortcut view keypress listenting.
			document.removeEventListener('keypress', this._curryCallback);
		}
	}

	/**
	 * Handles a keypress of a user and sends it towards 
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
		throw 'not implemented.';
	}
}