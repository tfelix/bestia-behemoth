Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Basic = function() {

	this._game = game;

};

Bestia.Engine.Indicator.Basic.prototype.initialize = function() {

	this.marker = this._game.add.graphics();
	this.marker.lineStyle(2, 0xffffff, 1);
	this.marker.drawRect(0, 0, 32, 32);
	
	this.game.input.addMoveCallback(this.updateMarker, this);
	this.game.input.onDown.add(this.clickHandler, this);
	
};

/**
 * Callback is called if the engine 
 */
Bestia.Engine.Indicator.Basic.prototype.onMouseMove = function() {

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

/**
 * Called when the engine renders the indicator each tick.
 */
Bestia.Engine.Indicator.Basic.prototype.onUpdate = function() {

	var pointer = this._game.input.activePointer;

	var cords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);

	this.marker.x = cords.x;
	this.marker.y = cords.y;	
};