Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Basic = function(manager, ctx) {

	if (!manager) {
		throw new Error("Manager can not be null.");
	}

	this._ctx = manager.ctx;
	this._manager = manager;

	this._marker = null;
};

Bestia.Engine.Indicator.Basic.prototype.activate = function() {
	this._ctx.game.input.addMoveCallback(this._onMouseMove, this);
	this._ctx.game.input.onDown.add(this._onClick, this);
	this._ctx.game.world.add(this._marker);
};

Bestia.Engine.Indicator.Basic.prototype.deactivate = function() {
	this._ctx.game.input.deleteMoveCallback(this._onMouseMove, this);
	this._ctx.game.input.onDown.remove(this._onClick, this);
	this._ctx.game.world.remove(this._marker);
};

/**
 * Override an create all needed game objects here.
 */
Bestia.Engine.Indicator.Basic.prototype.create = function() {
	// no op.
};

Bestia.Engine.Indicator.Basic.prototype.loadAssets = function() {
	// no op.
};

/**
 * If there are static assets which the indicator needs one can load them in
 * here. The method is called by the system before the general operation of the
 * engine starts.
 */
Bestia.Engine.Indicator.Basic.prototype.preLoadAssets = function() {
	// no op.
};

Bestia.Engine.Indicator.Basic.prototype._requestActive = function() {
	return this._manager.requestActive(this);
};

/**
 * Callback is called if the engine
 */
Bestia.Engine.Indicator.Basic.prototype._onMouseMove = function() {
	if (this._marker === null) {
		return;
	}

	var pointer = this._ctx.game.input.activePointer;

	// From px to tiles and back.
	var cords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
	Bestia.Engine.World.getPxXY(cords.x, cords.y, cords);

	this._marker.x = cords.x;
	this._marker.y = cords.y;

	// TODO Check if we are on an non walkable tile. Hide cursor here.
};