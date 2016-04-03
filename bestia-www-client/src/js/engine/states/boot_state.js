Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * State is triggered once when the game starts. It will preload all the really 
 * needed assets in order to to a proper loading screen and do some basic game setup.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.BootState = function(pubsub) {

	this._pubsub = pubsub;
	
};

/**
 * Preload all the needed assets in order to display a loading screen.
 */
Bestia.Engine.States.BootState.prototype.preload = function() {
	
};

Bestia.Engine.States.BootState.prototype.create = function() {
	
	this._pubsub.publish(Bestia.Signal.ENGINE_BOOTED);
	this.game.state.start('initial_loading');
};