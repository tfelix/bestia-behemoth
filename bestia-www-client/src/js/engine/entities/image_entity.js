Bestia.Engine.ImageEntity = function(game, uuid, x, y, desc) {
	Bestia.Engine.BasicEntity.call(this, game);

	this.uuid = uuid;
	this._data = desc;

	this.setPosition(x, y);

};

Bestia.Engine.ImageEntity.prototype = Object.create(Bestia.Engine.BasicEntity.prototype);
Bestia.Engine.ImageEntity.prototype.constructor = Bestia.Engine.ImageEntity;

Bestia.Engine.ImageEntity.prototype.setSprite = function(spriteName) {

	this._sprite = this._game.add.image(0, 0, spriteName);
	
	// Enable input.
	this._sprite.inputEnabled = true;
	this._sprite.events.onInputDown.add(this._onClickHandler, this);

	this._setupSprite(this._sprite, this._data);

	// Re-set position so the sprite gets now postioned.
	this.setPosition(this.position.x, this.position.y);
};

Bestia.Engine.ImageEntity.prototype._onClickHandler = function() {
	alert("geht");
};

/**
 * Helper function to setup a sprite with all the information contained inside a
 * description object.
 * 
 * @param sprite
 * @param descObj
 */
Bestia.Engine.ImageEntity.prototype._setupSprite = function(sprite, descObj) {

	// Setup the normal data.
	sprite.anchor = descObj.anchor || {
		x : 0.5,
		y : 1
	};

	// Sprite is invisible at first.
	sprite.alpha = 0;
};

Bestia.Engine.ImageEntity.prototype.show = function() {
	this._sprite.alpha = 1;
};

Bestia.Engine.ImageEntity.prototype.appear = function() {
	this._sprite.alpha = 1;
};

/**
 * Sprite position is updated with the data from the server.
 */
Bestia.Engine.ImageEntity.prototype.update = function(msg) {
	var x = msg.x - this.position.x;
	var y = msg.y - this.position.y;
	var distance = Math.sqrt(x * x + y * y);

	// Directly set distance if too far away.
	if (distance > 1.5) {
		this.setPosition(msg.x, msg.y);
		return;
	}
	
	this.setPosition(msg.x, msg.y);
};

/**
 * Can be used to position related child sprites (like weapons or a head) frame
 * by frame.
 */
Bestia.Engine.ImageEntity.prototype.update = function() {

};

/**
 * Stops rendering of this entity and removes it from the scene.
 */
Bestia.Engine.ImageEntity.prototype.remove = function() {

	this._sprite.destroy();

};