import NullIndicator from './NullIndicator';
import MoveIndicator from './MoveIndicator.js';
import ItemCastIndicator from './ItemCastIndicator.js';
import BasicAttackIndicator from './BasicAttackIndicator.js';

/**
 * The manager is responsible for switching the indicator depending on the needs
 * of the engine. It listens to various events (usage of an item for example)
 * and in case of this will switch the indicator. This indicator then gets the
 * control of the inputs and must react accordingly.
 * <p>
 * The manager does also listen to change requests from the outside. So it is
 * possible to react upon hover effects over sprites for example.
 * </p>
 */
export default class IndicatorManager {
	
	constructor() {

		/**
		 * Holds all the registered indicators.
		 */
		this._indicators = [];

		/**
		 * We will buffer the calls to the indicator in order to re-display
		 * them.
		 * 
		 * @private
		 */
		this._indicatorStack = [];

		/**
		 * Holds the currently active indicator.
		 * 
		 * @private
		 */
		this._active = null;
		
		this._moveIndicator = new MoveIndicator(this);
		this._nullIndicator = new NullIndicator(this);
		
		// Register the available indicators.
		this._indicators.push(this._moveIndicator);
		//this._indicators.push(new ItemCastIndicator(this));
		this._indicators.push(new BasicAttackIndicator(this));
	}
	
	/**
	 * This will hide and disable all indicators.
	 */
	hide() {
		this.requestActive(this._nullIndicator, true);
	}
	
	/**
	 * This will re-enable the indicators if they were hidden before.
	 */
	show() {
		// We can only show when previously hidden.
		if(this._active !== this._nullIndicator) {
			return;
		}
		this.dismissActive();
	}

	/**
	 * Shows the default pointer. It will also clear the pointer stack.
	 */
	showDefault() {
		this.requestActive(this._moveIndicator);
		this._indicatorStack = [];
	}

	/**
	 * Will trigger all load events on the registered indicators. This should be
	 * called in the initial load event of the engine to fetch all presets the indicator
	 * need.
	 */
	load() {
		this._indicators.forEach(function(x) {
			x.load();
		}, this);
	}

	/**
	 * Triggers all create events on the registered indicators. This should be
	 * called in the create event of phaser.
	 */
	create() {
		this._indicators.forEach(function(x) {
			x.create();
		}, this);
	}
	
	/**
	 * Called each tick in case there is a need to perform some changes
	 * depending on the game tick.
	 */
	update() {

	}

	/**
	 * An indicator can request to get displayed via the manager. The current
	 * active indicator is pushed to the stack. With dismissActive it will
	 * re-appear again.
	 * 
	 * @param force -
	 *            The indicator will not be checked if its okay to replace him
	 *            with the new indicator.
	 */
	requestActive(indicator, force = false) {
		if (this._active !== null) {
			// Ask the active pointer if he allows to be overwritten by the new
			// indicator.
			if (!force && !this._active.allowOverwrite(indicator)) {
				return;
			}

			this._indicatorStack.push(this._active);
			this._active.deactivate();
		}

		this._active = indicator;
		this._active.activate();
	}

	/**
	 * No pushing to the indicator stack will happen when using this method.
	 * Otherwise its the same as requestActive.
	 */
	_setActive(indicator) {
		if (this._active !== null) {
			// Ask the active pointer if he allows to be overwritten by the new
			// indicator.
			if (!this._active.allowOverwrite(indicator)) {
				return;
			}
			this._active.deactivate();
		}
		this._active = indicator;
		this._active.activate();
	}

	/**
	 * The indicator can request to get dismissed. It will be replaced with last
	 * indicator.
	 */
	dismissActive() {
		if (this._indicatorStack.length === 0) {
			this._active = this._moveIndicator;
		} else {
			var indi = this._indicatorStack.pop();
			this._setActive(indi);
		}
	}

}