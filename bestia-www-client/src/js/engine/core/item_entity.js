Bestia.Engine.ItemEntity = function(game, uuid, x, y, spriteName) {
	Bestia.Engine.BasicEntity.call(this, game, x, y);

	this.uuid = uuid;
	this._game = game;

	var pos = Bestia.Engine.World.getPxXY(x, y);
	this._sprite = this._game.add.sprite(pos.x, pos.y, spriteName);
	this._sprite.alpha = 0;

	// bottom left of the item.
	this._sprite.anchor.setTo(0.5);
};


Bestia.Engine.ItemEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.ItemEntity.prototype.constructor = Bestia.Engine.ItemEntity;

Bestia.Engine.ItemEntity.prototype.show = function() {

	this._sprite.apha = 1;
	
};

/**
 * An item will drop to the ground from approx. 1.5m from above. So it has to
 * start 1.5 tiles "higher" then the landing tile.
 */
Bestia.Engine.ItemEntity.prototype.appear = function() {

	// Set the start position.
	var pos = Bestia.Engine.World.getPxXY(this._position.x, this._position.y - 1.5);
	var endY = this._sprite.y;
	this._sprite.y = pos.y;

	this._sprite.alpha = 1;
	this._game.add.tween(this._sprite).to({
		y : endY
	}, 550, Phaser.Easing.Linear.None, true);
};

