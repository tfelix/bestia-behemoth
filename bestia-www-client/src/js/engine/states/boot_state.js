/**
 * State is triggered once when the game starts.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.BootState = function() {


};

Bestia.Engine.States.BootState.prototype.create = function() {
	/* @ifdef DEVELOPMENT **
	//this.game.add.plugin(Phaser.Plugin.Debug);	
	/* @endif */
	
	this.game.state.start('connecting');
};