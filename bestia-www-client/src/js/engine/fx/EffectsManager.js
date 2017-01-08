import ChatFx from './ChatFx.js';
import DamageFx from './DamageFx.js';
import BrightnessFx from './BrightnessFx.js';
import RainFx from './RainFx.js';
import RangeMeasureFx from './RangeMeasureFx.js';
import DialogFx from './DialogFx.js';

/**
 * FX Manager which is responsible for effect generation and display. Effects
 * are not so important to be tracked liked entities. In fact they can be
 * generated directly by the server or also internally by the client if needed.
 * This might be temporary effects like damage displays or particle effects.
 * 
 * TODO There must be a way to communicate with the effects.
 * 
 * If this class gets too bloated it must be split into smaller fx manager only
 * responsible for their area.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
export default class EffectsManager {
	constructor(ctx) {
		if (!ctx.pubsub) {
			throw new Error('PubSub can not be undefined');
		}
	
		if (!ctx.game) {
			throw  new Error('Game can not be null.');
		}
		
		if (!ctx.loader) {
			throw new Error('Loader can not be undefined.');
		}
	
		/**
		 * Holds reference to all added effect instances.
		 * 
		 * @private
		 */
		this._effectInstances = [];
		
		this._ctx = ctx;
	
		// Add the instances to control certain effects depending on incoming
		// messages.
		this._effectInstances.push(new DamageFx(ctx));
		this._effectInstances.push(new ChatFx(ctx));
		this._effectInstances.push(new DialogFx(ctx));
		this._effectInstances.push(new BrightnessFx(ctx));
		this._effectInstances.push(new RainFx(ctx));
		this._effectInstances.push(new RangeMeasureFx(ctx));
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

