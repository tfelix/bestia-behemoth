/**
 * This entity is a bit different. It supports a head sprite which can be moved
 * async to the body sprite to give a certain more realistic look to the players
 * then the mobs.
 * 
 * @constructor
 * @this {Bestia.Engine.PlayerSpriteEntity}
 * @param {number}
 *            playerBestiaId The ID of the player bestia represented by this
 *            sprite.
 */
Bestia.Engine.MultispriteEntity = function(game, uuid, desc) {
	Bestia.Engine.SpriteEntity.call(this, game, uuid, -100, -100, desc);

	this._animOffset = {};

	this._multispriteData = desc.multiSprite;

};

Bestia.Engine.MultispriteEntity.prototype = Object.create(Bestia.Engine.SpriteEntity.prototype);
Bestia.Engine.MultispriteEntity.prototype.constructor = Bestia.Engine.MultispriteEntity;

Bestia.Engine.MultispriteEntity.prototype._getSubspriteAnimation = function(subsprite, currentAnim) {
	for (var i = 0; i < this._multispriteData.length; i++) {
		var ms = this._multispriteData[i];
		if (ms.id !== subsprite) {
			continue;
		}
		
		// Look for the name.
		for(var j = 0; j < ms.animations.length; j++) {
			if(ms.animations[j].triggered === currentAnim) {
				return ms.animations[j].name;
			}
		}
	}
	
	// No anim found.
	return null;
};

Bestia.Engine.MultispriteEntity.prototype.setSprite = function(spriteName) {
	Bestia.Engine.SpriteEntity.prototype.setSprite.call(this, spriteName);
	
	// Add the multi sprites if there are some of them.
	var multisprites = this._data.multiSprite;
	if (Array.isArray(multisprites)) {
		for (var i = 0; i < multisprites.length; i++) {
			
			var ms = multisprites[i];

			var sprite = this._sprite.addChild(this._game.make.sprite(ms.defaultAnchor.x, ms.defaultAnchor.y, ms.id));
			sprite.anchor.setTo(0.5, 1);
			sprite.scale.setTo(ms.scale);
			sprite.frameName = 'bottom.png';
		}
	}
	
	
};

Bestia.Engine.MultispriteEntity.prototype._getSubspriteOffset = function(subsprite, currentAnim, currentFrame) {

	for (var i = 0; i < this._multispriteData.length; i++) {
		var ms = this._multispriteData[i];
		if (ms.id !== subsprite) {
			continue;
		}
		
		// Look for the name.
		for(var j = 0; j < ms.animations.length; j++) {
			if(ms.animations[j].name === currentAnim) {
				return ms.animations[j].offsets[currentFrame];
			}
		}
	}
	
	return {x: 0, y: 0};
};

/**
 * Depending on current animation update the sprite offset.
 */
Bestia.Engine.MultispriteEntity.prototype.update = function() {
	if (this._sprite === null) {
		return;
	}

	var curAnim = this._sprite.animations.name;
	var curFrame = this._sprite.animations.currentFrame;

	var offset = this._animOffset[curAnim][curFrame];
	var subSpriteName = this._animationLookup[curAnim];
};
