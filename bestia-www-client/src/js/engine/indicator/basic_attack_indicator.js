Bestia.Engine.Indicator = Bestia.Engine.Indicator || {};

/**
 * This indicator will manage the 
 * 
 * @class Bestia.Engine.Indicator
 */
Bestia.Engine.Indicator.BasicAttack = function(manager) {
	Bestia.Engine.Indicator.Basic.call(this, manager);

	this._marker = null;
	
	// Listen for activation signal.
	this._ctx.pubsub.subscribe(Bestia.Signal.ENGINE_REQUEST_INDICATOR, this._handleIndicator.bind(this));
};

Bestia.Engine.Indicator.BasicAttack.prototype = Object.create(Bestia.Engine.Indicator.Basic.prototype);
Bestia.Engine.Indicator.BasicAttack.prototype.constructor = Bestia.Engine.Indicator.BasicAttack;


Bestia.Engine.Indicator.BasicAttack.prototype.activate = function() {
	this._ctx.game.input.onDown.add(this._onClick, this);
	this._ctx.game.world.add(this._marker);
};

Bestia.Engine.Indicator.BasicAttack.prototype.deactivate = function() {
	this._ctx.game.input.onDown.remove(this._onClick, this);
	this._ctx.game.world.remove(this._marker);
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
	if(data.handle === 'basic_attack_over') {
		
		data.sprite.addChild(this._marker);
		
		this._marker.x = 0;
		this._marker.y = 0;
		
		this._requestActive();
	}else if(data.handle === 'basic_attack_out') {
		this._manager.dismissActive();
	}
};

Bestia.Engine.Indicator.ItemCast.prototype._onClick = function(pointer) {

	if (pointer.button === Phaser.Mouse.RIGHT_BUTTON) {
		// Was canceled.
		this._manager.showDefault();
		return;
	}

	
};
