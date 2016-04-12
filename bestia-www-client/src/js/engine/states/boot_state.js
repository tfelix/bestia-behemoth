Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * State is triggered once when the game starts. It will preload all the really
 * needed assets in order to to a proper loading screen and do some basic (and
 * very important!) game setup.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.BootState = function(engine) {

	this._engine = engine;

};

/**
 * Preload all the needed assets in order to display a loading screen.
 */
Bestia.Engine.States.BootState.prototype.preload = function() {

};

Bestia.Engine.States.BootState.prototype.create = function() {
	
	// Setup the game context.
	var ctx = this._engine.ctx;
	
	ctx.game = this.game;

	ctx.pubsub.publish(Bestia.Signal.ENGINE_BOOTED);
};