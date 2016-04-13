Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.Move = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);
	
	this._effect = null;
};

Bestia.Engine.Indicator.Move.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.Move.prototype.constructor = Bestia.Engine.Indicator.Move;


Bestia.Engine.Indicator.Basic.prototype._onClick = function(pointer) {
	
	// Display fx.
	this._effect.alpha = 1;
	this._ctx.game.add.tween(this._effect).to({alpha: 0}, 500, Phaser.Easing.Cubic.Out, true);
	

	var player = this._ctx.getPlayerEntity();
	
	if(player === null) {
		return;
	}

	var start = player.position;
	var goal = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);

	var path = this._ctx.zone.findPath(start, goal).nodes;

	if (path.length === 0) {
		return;
	}

	path = path.reverse();
	var msg = new Bestia.Message.BestiaMove(player.playerBestiaId, path, this._ctx.playerBestia);
	this._ctx.pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);

	// Start movement locally as well.
	player.moveTo(path);
};

/**
 * Override an create all needed game objects here.
 */
Bestia.Engine.Indicator.Basic.prototype.load = function() {
	this._ctx.game.load.spritesheet('cursor', this._ctx.url.getIndicatorUrl('cursor'), 32, 32);
};

/**
 * Override an create all needed game objects here.
 */
Bestia.Engine.Indicator.Basic.prototype.create = function() {
	this._marker = this._ctx.game.make.sprite(0, 0, 'cursor');
	this._marker.animations.add('blink');
	this._marker.animations.play('blink', 1, true);
	
	var graphics = this._ctx.game.add.graphics(0, 0);
	graphics.beginFill(0x42D65D);
    graphics.drawRect(6, 6, 20, 20);
    graphics.endFill();
    graphics.alpha = 0;
    
    this._effect = graphics;
    
    this._marker.addChild(this._effect);
};

