
import Signal from '../../io/Signal.js';

/**
 * The state is triggered if a complete now part of a map is loaded and thus the
 * engine needs some time to get all the needed data. Usually we must ask the
 * server for the current chunks of the player and then load the following data:
 * <p>
 * <ul>
 * <li>Map chunks</li>
 * <li>Sounds</li>
 * <li>Entity sprites</li>
 * <li>Attack sprites/sounds of these entities</li>
 * </ul>
 * </p>
 * 
 * @constructor
 */
export default class LoadingState  {
	constructor(context) {		
		this._ctx = context;

		/**
		 * Reference to the pubsub system.
		 * 
		 * @private
		 */
		this._pubsub = context.pubsub;
	}
	
	_checkFinishedLoading() {
		this._loadingCounter--;
		
		if(this._loadingCounter == 0) {
			this._pubsub.publish(Signal.ENGINE_FINISHED_MAPLOAD);
		}
	}
	
	preload() {
		// Set loading counter.
		this._loadingCounter = 1;
		
		// Announce loading.
		this._pubsub.publish(Signal.ENGINE_PREPARE_MAPLOAD);

		// Prepare the loading screen.
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);
		
		// Create new multisprite entity from player bestia.
		//this._ctx.entityFactory.build({}, this._checkFinishedLoading.bind(this));
		
		let chunks = this._ctx.renderer.tile.getVisibleChunks();
		this._ctx.renderer.tile.loadChunks(chunks, this._checkFinishedLoading.bind(this));
	}
}

