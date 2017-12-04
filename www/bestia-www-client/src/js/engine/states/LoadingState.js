import Signal from '../../io/Signal.js';
import TileRender from '../renderer/TileRenderer';
import { engineContext } from '../EngineData';
import EntitySyncRequestMessage from '../../message/EntitySyncRequestMessage';

/**
 * The state is triggered if a complete now part of a map is loaded and thus the
 * engine needs some time to get all the needed data. Usually we must ask the
 * server for the current chunks of the player and then load the following data:
 * <p>
 * <ul>
 * <li>Map chunks</li>
 * <li>Nearby Sounds</li>
 * <li>Nearby entity sprites</li>
 * <li>Attack sprites/sounds of these entities</li>
 * </ul>
 * </p>
 * 
 * @constructor
 */
export default class LoadingState {

	constructor() {
		// no op.
	}

	/**
	 * Checks if all loading operations have been performed by counting down a
	 * latch. If loading has finished we proceed with the initialization.
	 */
	_checkFinishedLoading() {
		this._loadingCounter--;

		if (this._loadingCounter === 0) {
			engineContext.pubsub.publish(Signal.ENGINE_FINISHED_MAPLOAD);
		}
	}

	preload() {

		// Set loading counter (we load two assets)
		this._loadingCounter = 1;

		// Announce loading.
		engineContext.pubsub.publish(Signal.ENGINE_PREPARE_MAPLOAD);

		// Prepare the loading screen.
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);

		let tileRender = engineContext.renderManager.getRender(TileRender.NAME);
		let chunks = tileRender.getVisibleChunks();
		tileRender.loadChunks(chunks, this._checkFinishedLoading.bind(this));

		// Request a loading of all entities.
		let msg = new EntitySyncRequestMessage();
		engineContext.pubsub.send(msg);
	}

	create() {
		var style = {
			font : 'bold 32px Arial',
			fill : '#fff',
			boundsAlignH :'center',
			boundsAlignV : 'middle'
		};
		var txt = this.game.add.text(this.game.world.centerX, this.game.world.centerY, 'Loading', style);
		txt.anchor.set(0.5);
		txt.align = 'center';
	}
}

