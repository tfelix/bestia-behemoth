Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Basic = function(manager, ctx) {

	if(manager === null) {
		throw new Error("Manager can not be null.");
	}
	
	if(ctx === null) {
		throw new Error("EngineContext can not be null.");
	}
	
	this._ctx = ctx;
	this._manager = manager;
	
	this._marker = null;
};

Bestia.Engine.Indicator.Basic.prototype.activate = function() {
	this._game.input.addMoveCallback(this._onMouseMove, this);
	this._game.input.onDown.add(this._onClick, this);
	this._game.world.add(this._marker);
};

Bestia.Engine.Indicator.Basic.prototype.deactivate = function() {
	this._game.input.deleteMoveCallback(this._onMouseMove, this);
	this._game.input.onDown.remove(this._onClick, this);
	this._game.world.remove(this._marker);
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

	var pointer = this._game.input.activePointer;

	// From px to tiles and back.
	var cords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
	Bestia.Engine.World.getPxXY(cords.x, cords.y, cords);

	this.marker.x = cords.x;
	this.marker.y = cords.y;

	// TODO Check if we are on an non walkable tile. Hide cursor here.
};