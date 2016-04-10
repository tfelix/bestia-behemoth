Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Move = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);
	
	this._marker = this._game.make.sprite(0, 0, 'cursor');
	this._marker.animations.add('blink');
	this._marker.animations.play('blink', 1, true);
};

Bestia.Engine.Indicator.Move.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.Move.prototype.constructor = Bestia.Engine.Indicator.Move;


Bestia.Engine.Indicator.Basic.prototype._onClick = function(pointer) {

	var player = this._state.getPlayerEntity();
	
	if(player === null) {
		return;
	}

	var start = player.position;
	var goal = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);

	var path = this._world.findPath(start, goal).nodes;

	if (path.length === 0) {
		return;
	}

	path = path.reverse();
	var msg = new Bestia.Message.BestiaMove(this.bestia.playerBestiaId(), path, player.walkspeed);
	this._pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);

	// Start movement locally as well.
	player.moveTo(path);
};