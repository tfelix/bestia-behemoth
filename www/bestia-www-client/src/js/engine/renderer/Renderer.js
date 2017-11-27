
/**
 * A renderer is a important part of the game engine. It renders important and big
 * graphic assets like for example the tilemap itself and also the weather system for
 * example. It closes the gap between the bestia data model and the phaser game engine.
 */
export default class Renderer {
	
	constructor() {
		this._isDirty = false;
		
	}
	
	/**
	 * Returns the name of the renderer. Must be overwritten by childs.
	 */
	get name() {
		return 'OVERRIDE_ME';
	}
	
	/**
	 * Reports if the renderer is dirty and should be rendered.
	 */
	isDirty() {
		return this._isDirty;
	}
	
	/**
	 * Clears all data.
	 */
	clear() {
		throw 'This method must be overwritten.';
	}

	/**
	 * Is called if the stage is created and initial setups should be done in here.
	 */
	create() {
		// no op.
	}
	
	/**
	 * This is called by the render manager in each update step of the render
	 * loop if this class reports as dirty.
	 */
	update() {
		throw 'This method must be overwritten.';
	}

	/**
	 * This method is called when the loading procedure starts. Every asset the renderer might statically need 
	 * to do its job can be pre-loaded here. During runtime of course the is the possibility to perform a on demand
	 * loading of assets if not all are known beforehand.
	 */
	load() {
		// no op.
	}
}