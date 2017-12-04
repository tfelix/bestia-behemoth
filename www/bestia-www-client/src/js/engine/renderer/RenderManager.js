import LOG from '../../util/Log';

/**
 * The render manager controls the single render layers of the engine and is responsible
 * for calling them in the update step if the renderer mark themselves as dirty.
 */
export default class RenderManager {

	constructor() {
		this._renderer = [];
		this._named = {};
	}

	/**
	 * Adds a new renderer to this manager.
	 * 
	 * @param {Renderer} render A new render to be attached to the managers.
	 */
	addRender(render) {
		this._renderer.push(render);

		// If it has a name attach it to the named renderer.
		if (render.name) {
			this._named[render.name] = render;
		}
	}

	/**
	 * Returns the renderer for the given name.
	 */
	getRender(name) {
		if (!this._named.hasOwnProperty(name)) {
			return null;
		}
		return this._named[name];
	}

	/**
	 * Load the static assets from all registered renderer.
	 */
	load(game) {
		LOG.debug('Loading static render assets.');
		this._renderer.forEach((r) => {
			r.load(game);
		});
	}

	/**
	 * Clears all renderer resources. 
	 * This basically frees all resources used up by the renderers.
	 * This does NOT remove the renderer from the manager it only clears
	 * the data used up by them.
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
			if (r.isDirty()) {
				r.update();
			}
		}, this);
	}

	/**
	 * Creates the renderer and sets up resources which the renderer need.
	 */
	create() {
		this._renderer.forEach((r) => {
			r.create();
		}, this);
	}
}