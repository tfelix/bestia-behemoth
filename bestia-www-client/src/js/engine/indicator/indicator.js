Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Basic = function(game) {

	this._game = game;
	
	this.marker = this._game.add.sprite(0, 0, 'cursor');
	this.marker.animations.add('blink');
	this.marker.animations.play('blink', 1, true);
	
	this._game.input.addMoveCallback(this._onMouseMove, this);
	//this._game.input.onDown.add(this._onClick, this);
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

Bestia.Engine.Indicator.Basic.prototype.onClick = function() {

	var player = this._getPlayerEntity();

	var start = player.position;
	var goal = Bestia.Engine.World.getTileXY(this.game.input.worldX, this.game.input.worldY);

	var path = this.bestiaWorld.findPath(start, goal).nodes;

	if (path.length === 0) {
		return;
	}

	path = path.reverse();
	var msg = new Bestia.Message.BestiaMove(this.bestia.playerBestiaId(), path, player.walkspeed);
	this.pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);

	// Start movement locally as well.
	player.moveTo(path);
	
};