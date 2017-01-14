import ReferenceName from '../ReferenceName';
import TileRenderer from './TileRenderer';

/**
 * Manages and contains all for the graphics.
 */
export default class RenderManager {
	
	constructor(pubsub) {
		this._renderer = [];
		this._named = {};
		
		pubsub.setRef(ReferenceName.RenderManager, this);
		
		this.addRender(new TileRenderer(pubsub));
	}
	
	/**
	 * Adds a new renderer to this manager.
	 */
	addRender(render) {
		this._renderer.push(render);
		this._named[render.name] = render;
	}
	
	/**
	 * Returns the renderer for the given name.
	 */
	getRender(name) {
		if(!this._named.hasOwnProperty(name)) {
			return null;
		}
		return this._named[name];		
	}
	
	/**
	 * Clears all renderer.
	 */
	clear() {
		this._renderer.forEach((r) => {
			r.clear();
		});
	}
	
	/**
	 * Loops through all rengistered renderer and performs an update call if
	 * they report themselves as dirty.
	 */
	update() {
		this._renderer.forEach((r) => {
			if(r.isDirty) {
				r.update();
			}
		});
	}
}