
import Indicator from './Indicator.js';

/**
 * Invisible indicator, doing nothing.
 * 
 * @class Bestia.Engine.Indicator
 */
export default class NullIndicator extends Indicator {
	constructor(manager) {
		super(manager);
	}
	
	_onClick() {
		
		// no op.
	}
	
	activate() {
		// no op.
	}
	
	deactivate() {
		// no op.
	}

	/**
	 * Override an create all needed game objects here.
	 */
	load() {
		// no op.
	}

	/**
	 * Override an create all needed game objects here.
	 */
	create() {
		// no op.
	}
}
