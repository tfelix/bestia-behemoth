Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * The state is triggered if a new map is loaded.
 * Displays while the engine is loading files to display the next map. 
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.LoadingState = function(engine, urlHelper) {

	this.bestia = null;
	
	/**
	 * Current asset and map loading progress.
	 * 
	 * @private
	 */
	this._currentProgress = 0;
	
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
	this._pubsub = engine.pubsub;
	
	this._urlHelper = urlHelper;
};

Bestia.Engine.States.LoadingState.prototype.init = function(bestia) {
	this.bestia = bestia;
	
	// Announce loading.
	this._pubsub.publish(Bestia.Signal.ENGINE_PREPARE_MAPLOAD);

	console.debug("Loading map: " + this.bestia.location());

	// Prepare the loading screen.
	this.gfx = this.add.graphics(0, 0);
	this.gfx.beginFill(0xFF0000, 1);
};

Bestia.Engine.States.LoadingState.prototype.preload = function() {
	
	// Load the mapfile itself.
	var mapDbName = this.bestia.location();
	var packUrl = this._urlHelper.getMapPackUrl(mapDbName);
	this.load.pack(mapDbName, packUrl);
	

	this.load.onFileComplete.add(this.fileCompleted, this);
};

Bestia.Engine.States.LoadingState.prototype.loadUpdate = function() {

	this.game.debug.text("Loading", 10, 30, '#FFFFFF');
	var maxWidth = this.game.width - 20;
	this.gfx.drawRect(10, 60, (maxWidth * this._currentProgress / 100), 20);

};

Bestia.Engine.States.LoadingState.prototype.create = function() {
	
	this._pubsub.publish(Bestia.Signal.ENGINE_FINISHED_MAPLOAD);

};

Bestia.Engine.States.LoadingState.prototype.fileCompleted = function(progress) {

	this._currentProgress = progress;
	this.text = "Complete: " + progress + "%";

};
