/**
 * State is established if the connection is lost. It will wait for a
 * reconnection event to occure and start the loading phase.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.ConnectingState = function(engine) {

	/**
	 * Engine reference.
	 * 
	 * @property {Bestia.Engine}
	 */
	this._engine = engine;
};

Bestia.Engine.States.ConnectingState.prototype = {
	create : function() {
		var style = {
			font : "bold 32px Arial",
			fill : "#fff",
			boundsAlignH : "center",
			boundsAlignV : "middle"
		};
		this.game.add.text(40, 40, 'Connecting...', style);
		
		// Signal that the engine has loaded. Triggers connect.
		this._engine.pubsub.publish('engine.loaded');
	}
};

Bestia.Engine.States.ConnectingState.prototype.constructor = Bestia.Engine.States.ConnectingState;
