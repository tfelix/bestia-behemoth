


export default class Renderer {
	
	constructor() {
		this._isDirty = false;
		
	}
	
	/**
	 * Returns the name of the renderer. Must be overwritten by childs.
	 */
	get name() {
		throw 'This method must be overwritten.';
	}
	
	/**
	 * Reports if the renderer is dirty and should be rendered.
	 */
	get isDirty() {
		return this._isDirty;
	}
	
	/**
	 * Clears all data.
	 */
	clear() {
		throw 'This method must be overwritten.';
	}
	
	/**
	 * This is called by the render manager in each update step of the render
	 * loop if this class reports as dirty.
	 */
	update() {
		throw 'This method must be overwritten.';
	}	
}