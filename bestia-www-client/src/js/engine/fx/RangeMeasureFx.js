
/**
 * The range measure effect will display a certain range on the ground. It can
 * be used to communicate usable ranges to the user.
 * 
 * @param {Bestia.Engine.EntityCacheManager}
 *            cache
 */
export default class RangeMeasureFx {

	constructor(pubsub, cache, game, groups) {
		this._pubsub = pubsub;
	
		this._game = game;
	
		this._cache = cache;
	
		this._groups = groups;
		
		this._range = 0;
		this._show = false;
	}
	
	destroy () {
		
	}

	create() {

	}

	update() {

	}
	
	get range() {
		return this._range;
	}
	
	set range(value) {
		this._range = value;
	}

	get show() {
		return this._range;
	}
	
	set show(value) {
		this._show = value;
	}
}