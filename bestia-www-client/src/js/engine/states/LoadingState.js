
import Signal from '../../io/Signal.js';

/**
 * The state is triggered if a new map is loaded. Displays while the engine is
 * loading files to display the next map.
 * 
 * @constructor
 */
export default class LoadingState  {
	constructor(engine) {
		this.bestia = null;
		
		/**
		 * Current asset and map loading progress.
		 * 
		 * @private
		 */
		this._currentProgress = 0;
		
		this._ctx = engine.ctx;
		
		/**
		 * Reference to the bestia engine.
		 * 
		 * @private
		 */
		this._engine = engine;

		/**
		 * Reference to the pubsub system.
		 * 
		 * @private
		 */
		this._pubsub = engine.ctx.pubsub;
		
		this._urlHelper = engine.ctx.url;
	}
	
	init() {
		// Announce loading.
		this._pubsub.publish(Signal.ENGINE_PREPARE_MAPLOAD);

		console.debug("Loading map: " + this._ctx.playerBestia.location());

		// Prepare the loading screen.
		this.gfx = this.add.graphics(0, 0);
		this.gfx.beginFill(0xFF0000, 1);
	}

	preload() {
		
		// Load the mapfile itself.
		var mapDbName = this._ctx.playerBestia.location();
		var packUrl = this._urlHelper.getMapPackUrl(mapDbName);
		this.load.pack(mapDbName, packUrl);
		
		// TODO Pre load the map assoziated data (entities, sounds, etc)
		

		this.load.onFileComplete.add(this.fileCompleted, this);
	}

	loadUpdate() {

		this.game.debug.text("Loading", 10, 30, '#FFFFFF');
		var maxWidth = this.game.width - 20;
		this.gfx.drawRect(10, 60, (maxWidth * this._currentProgress / 100), 20);

	}

	create() {
		
		this._pubsub.publish(Signal.ENGINE_FINISHED_MAPLOAD);

	}

	fileCompleted(progress) {

		this._currentProgress = progress;
		this.text = "Complete: " + progress + "%";

	}
}

