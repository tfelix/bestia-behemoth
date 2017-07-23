import Signal from '../../io/Signal.js';
import TileRender from '../renderer/TileRenderer';
import EntitySyncRequestMessage from '../../message/EntitySyncRequestMessage';

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
	constructor(ctx) {		
		
		this._ctx = ctx;
	}
	
	/**
	 * Checks if all loading operations have been performed by counting down a
	 * latch. If loading has finished we proceed with the initialization.
	 */
	_checkFinishedLoading() {
		this._loadingCounter--;
		
		if(this._loadingCounter === 0) {
			this._ctx.pubsub.publish(Signal.ENGINE_FINISHED_MAPLOAD);
		}
	}
	
	preload() {
		// Extend with all needed objects.
		
		let tileRender = this._ctx.render.getRender(TileRender.NAME);
		
		// Set loading counter (we load two assets)
		this._loadingCounter = 2;
		
		// Announce loading.
		this._ctx.pubsub.publish(Signal.ENGINE_PREPARE_MAPLOAD);

		// Prepare the loading screen.
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);
		
		// Create new multisprite entity from player bestia. This call will
		// initialize a loading process even if visible sprite gets destroyed by
		// changing game states.
		let pb = this._ctx.playerBestia;
		let updateMsg = {s: pb.sprite(), a: 'APPEAR', s: {s: pb.sprite(), t: pb.spriteType()}};
		this._ctx.entityFactory.load(updateMsg, this._checkFinishedLoading.bind(this));
		
		let chunks = tileRender.getVisibleChunks();
		tileRender.loadChunks(chunks, this._checkFinishedLoading.bind(this));

		// Request a loading of all entities.
		let msg = new EntitySyncRequestMessage();
		this._ctx.pubsub.send(msg);
	}
}

