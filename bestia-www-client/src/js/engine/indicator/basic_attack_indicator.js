Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * This indicator will manage the
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.BasicAttack = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);
	
	/**
	 * Max range of the basic attack.
	 * @constant
	 */
	this.RANGE = 1;

	this._marker = null;

	this._targetSprite = null;

	// Listen for activation signal.
	this._ctx.pubsub.subscribe(Bestia.Signal.ENGINE_REQUEST_INDICATOR, this._handleIndicator.bind(this));
};

Bestia.Engine.Indicator.BasicAttack.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.BasicAttack.prototype.constructor = Bestia.Engine.Indicator.BasicAttack;

Bestia.Engine.Indicator.BasicAttack.prototype.activate = function() {
	this._ctx.game.input.onDown.add(this._onClick, this);
	this._marker.reset();
	this._targetSprite.addChild(this._marker);
};

Bestia.Engine.Indicator.BasicAttack.prototype.deactivate = function() {
	this._ctx.game.input.onDown.remove(this._onClick, this);
	// this._ctx.game.world.remove(this._marker);
	this._marker.kill();
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.BasicAttack.prototype.load = function() {
	this._ctx.game.load.image('cursor_atk', this._ctx.url.getSpriteUrl('cursor_atk'));
};

/**
 * Preload all needed assets.
 */
Bestia.Engine.Indicator.BasicAttack.prototype.create = function() {
	this._marker = this._ctx.game.make.sprite(0, 0, 'cursor_atk');
	this._marker.anchor.setTo(0.5, 0.5);
	this._marker.angle = 0;
};

Bestia.Engine.Indicator.BasicAttack.prototype._handleIndicator = function(_, data) {
	if (data.handle === 'basic_attack_over') {
		this._targetSprite = data.sprite;
		
		// Do some wiring. If the sprite dies we need to give up controls.
		this._targetSprite.events.onDestroy.add(function(){
			this._manager.dismissActive();
		}, this);
		
		this._requestActive();
	} else if (data.handle === 'basic_attack_out') {
		this._manager.dismissActive();
	}
};

Bestia.Engine.Indicator.BasicAttack.prototype._onClick = function(pointer) {

	// If we are close enough we send a attack request to the server, if we are
	// too far away we will move towards the target.
	if (pointer.button !== Phaser.Mouse.LEFT_BUTTON) {
		return;
	}

	// Publish the cast information.
	var player = this._ctx.getPlayerEntity();
	var pointerCords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
	
	var d = Bestia.Engine.World.getDistance(player.position, pointerCords);
	
	if(d > this.RANGE) {
		// Move to target.
		alert("move" + d);
	} else {
		// Attack.
		alert("Attack" + d);
	}
};
