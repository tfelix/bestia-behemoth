Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Basic = function(game, pubsub, bestiaWorld, state) {

	this._game = game;
	
	this._pubsub = pubsub;
	
	this._world = bestiaWorld;
	
	this._state = state;
	
	this.marker = this._game.add.sprite(0, 0, 'cursor');
	this.marker.animations.add('blink');
	this.marker.animations.play('blink', 1, true);
	
	this._game.input.addMoveCallback(this._onMouseMove, this);
	this._game.input.onDown.add(this._onClick, this);
};

/**
 * Callback is called if the engine 
 */
Bestia.Engine.Indicator.Basic.prototype._onMouseMove = function() {
	var pointer = this._game.input.activePointer;

	// From px to tiles and back.
	var cords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
	Bestia.Engine.World.getPxXY(cords.x, cords.y, cords);

	this.marker.x = cords.x;
	this.marker.y = cords.y;
	
	// TODO Check if we are on an non walkable tile. Hide cursor here.
};

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