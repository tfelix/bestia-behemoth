
import Signal from '../../io/Signal.js';

/**
 * The state is triggered if a new map is loaded. It should inform the user that
 * we need to perform certain loading events until enough data is gathered to
 * start the visualization
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
	
	init() {
		// Announce loading.
		this._pubsub.publish(Signal.ENGINE_PREPARE_MAPLOAD);
		
		this._ctx.init();

		// Prepare the loading screen.
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);
		
		let chunks = this._ctx.renderer.tile.getVisibleChunks();
		chunks.forEach(function(chunk){
			this._ctx.renderer.tile.loadChunks(chunk);
		}.bind(this));
	}
	
	update() {
		
		//this.game.debug.text("Loading", 10, 30, '#FFFFFF');
		//var maxWidth = this.game.width - 20;
		//this.gfx.drawRect(10, 60, (maxWidth * this._currentProgress / 100), 20);
		
		this._pubsub.publish(Signal.ENGINE_FINISHED_MAPLOAD);
	}
}

