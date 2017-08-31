import ChatFx from './ChatFx.js';
import DamageFx from './DamageFx.js';
import DialogFx from './DialogFx.js';

const MAX_CACHE_SIZE = 50;

/**
 * FX Manager which is responsible for effect generation and display. Effects
 * are not so important to be tracked liked entities. In fact they can be
 * generated directly by the server or also internally by the client if needed.
 * This might be temporary effects like damage displays or particle effects.
 * 
 * If this class gets too bloated it must be split into smaller fx manager only
 * responsible for their area.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
export default class EffectsManager {
	constructor(ctx) {
	
		/**
		 * Holds reference to all added effect instances.
		 * 
		 * @private
		 */
		this._effectInstances = [];
		
		/**
		 * Holds a cache to all effects which can thus be reused by the effects.
		 * With this technique no additional object must be created all the
		 * time.
		 */
		this._cache = {};
		this.ctx = ctx;
	
		// Add the instances to control certain effects depending on incoming
		// messages.
		this._effectInstances.push(new DamageFx(this));
		this._effectInstances.push(new ChatFx(this));
		// this._effectInstances.push(new DialogFx(pubsub));
		// this._effectInstances.push(new BrightnessFx(pubsub));
		// this._effectInstances.push(new RainFx(pubsub));
		// this._effectInstances.push(new RangeMeasureFx(pubsub));
	}
	
	/**
	 * Caches the effect instance under a given key. If there are more objects
	 * then cache spaces available the object is destroyed by calling
	 * #remove();
	 */
	cacheEffect(key, obj) {
		if(!this._cache.hasOwnProperty(key)) {
			this._cache[key] = [];
		}
		
		if(this._cache[key].length < MAX_CACHE_SIZE) {
			this._cache[key].push(obj);
		} else {
			// Destroy the effect instance.
			obj.remove();
		}
	}
	
	/**
	 * Returns a previously cached object with the given key. If the cache was
	 * empty then null is returned.
	 * 
	 * @return A cached object or null if the cache was empty.
	 */
	getCachedEffect(key) {
		if(this._cache.hasOwnProperty(key)) {
			if(this._cache[key].length > 0) {
				let obj = this._cache[key].pop();
				return obj;
			}
		}
		
		return null;
	}
	
	/**
	 * Is called when the engine is inside the create method.
	 */
	create() {
		this._effectInstances.forEach(function(fx){
			if(fx.create !== undefined) {
				fx.create();
			}
		});
	}

	/**
	 * Is called when the engine is inside the update method.
	 */
	update() {
		this._effectInstances.forEach(function(fx){
			if(fx.update !== undefined) {
				fx.update();
			}
		});
	}

	/**
	 * Is called when the engine is inside the load method.
	 */
	load() {
		this._effectInstances.forEach(function(fx){
			if(fx.load !== undefined) {
				fx.load();
			}
		});
	}
}

