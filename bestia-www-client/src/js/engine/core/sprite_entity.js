Bestia.Engine.SpriteEntity = function(game, uuid, x, y, spriteName) {
	Bestia.Engine.BasicEntity.call(this, game);
	
	this.uuid = uuid;

	this._game = game;

	this._sprite = this._game.add.sprite(200, 200, spriteName);

	this._sprite.alpha = 0;

	// bottom left of the item.
	this._sprite.anchor.setTo(0.5);
};

Bestia.Engine.SpriteEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.SpriteEntity.prototype.constructor = Bestia.Engine.SpriteEntity;