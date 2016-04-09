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
Bestia.Engine.MultispriteEntity = function(game, uuid, x, y, data) {
	Bestia.Engine.SpriteEntity.call(this, game, uuid, x, y, data);

	this._animOffset = {};

	this._multispriteData = data.multiSprite;

	this._generateAnimationOffetData(data);

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
