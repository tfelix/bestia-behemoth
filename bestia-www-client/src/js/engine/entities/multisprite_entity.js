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

	this._multiSprites = [];
};

Bestia.Engine.MultispriteEntity.NULL_POS = {
	x : 0,
	y : 0
};

Bestia.Engine.MultispriteEntity.prototype = Object.create(Bestia.Engine.SpriteEntity.prototype);
Bestia.Engine.MultispriteEntity.prototype.constructor = Bestia.Engine.MultispriteEntity;

/**
 * Returns the name of the subsprite animation depending of the current "main"
 * animation running on the main sprite. Can be used to set the subsprite
 * animations after the animation of the main sprite has changed.
 * 
 * @param {string}
 *            subspriteName Name of the current subsprite.
 * @param currentAnim
 * @returns Name of the subsprite animation.
 */
Bestia.Engine.MultispriteEntity.prototype._getSubspriteAnimation = function(subspriteName, currentAnim) {
	for (var i = 0; i < this._data.multiSprite.length; i++) {
		var ms = this._data.multiSprite[i];
		if (ms.id !== subspriteName) {
			continue;
		}

		// Look for the name.
		for (var j = 0; j < ms.animations.length; j++) {
			if (ms.animations[j].triggered === currentAnim) {
				return ms.animations[j].name;
			}
		}
	}

	// No anim found.
	return null;
};

/**
 * Sets the sprite of the entity.
 * 
 * @param {string}
 *            spriteName - New name of the sprite.
 */
Bestia.Engine.MultispriteEntity.prototype.setSprite = function(spriteName) {
	Bestia.Engine.SpriteEntity.prototype.setSprite.call(this, spriteName);

	// Add the multi sprites if there are some of them.
	var multisprites = this._data.multiSprite;
	if (Array.isArray(multisprites)) {
		for (var i = 0; i < multisprites.length; i++) {

			var ms = multisprites[i];

			var sprite = this._game.make.sprite(ms.defaultAnchor.x, ms.defaultAnchor.y, ms.id);
			this._sprite.addChild(sprite);
			
			// Register all the animations.
			this._data.animations.forEach(function(anim) {
				var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
				this._sprite.animations.add(anim.name, frames, anim.fps, true, false);
			}, this);

			sprite.bestiaDefaultAnchor = ms.defaultAnchor;

			sprite.name = ms.id;
			sprite.anchor.setTo(0.5, 1);
			sprite.scale.setTo(ms.scale);
			sprite.frameName = 'bottom.png';

			this._multiSprites.push(sprite);
		}
	}

	// After setting the subsprites we must manually call set
	this._playSubspriteAnimation(this._sprite.animations.currentAnim.name);
};

/**
 * 
 * @param subsprite
 *            Name of the subsprite to look for its anchor offset.
 * @param currentAnim
 *            Currently running animation of the main sprite.
 * @param currentFrame
 *            The current frame of the main sprite.
 * @returns
 */
Bestia.Engine.MultispriteEntity.prototype._getSubspriteOffset = function(subsprite, currentAnim, currentFrame) {

	for (var i = 0; i < this._data.multiSprite.length; i++) {
		var ms = this._data.multiSprite[i];
		if (ms.id !== subsprite) {
			continue;
		}

		// Look for the name.
		for (var j = 0; j < ms.animations.length; j++) {
			if (ms.animations[j].triggered === currentAnim) {

				// Safety check.
				if (ms.animations[j].offsets.length <= currentFrame) {
					return Bestia.Engine.MultispriteEntity.NULL_POS;
				}

				return ms.animations[j].offsets[currentFrame];
			}
		}
	}

	return Bestia.Engine.MultispriteEntity.NULL_POS;
};

/**
 * It will keep all the subsprites with their animation in sync when the parent
 * animation was set.
 * 
 * @param name
 *            Name of the new animation to play.
 */
Bestia.Engine.MultispriteEntity.prototype.playAnimation = function(name) {
	Bestia.Engine.SpriteEntity.prototype.playAnimation.call(this, name);

	this._playSubspriteAnimation(name);
};

/**
 * Helper function since this must be called from multiple places.
 * 
 * @param mainAnimName
 */
Bestia.Engine.MultispriteEntity.prototype._playSubspriteAnimation = function(mainAnimName) {
	// Iterate over all subsprites an set their animations.
	this._multiSprites.forEach(function(s) {
		var subAnim = this._getSubspriteAnimation(s.name, mainAnimName);
		if(subAnim === null) {
			// no suitable sub animation found. Do nothing.
			return;
		}
		s.play(subAnim);
	}, this);
};

/**
 * Depending on current animation update the sprite offset.
 */
Bestia.Engine.MultispriteEntity.prototype.tickAnimation = function() {
	if (this._sprite === null) {
		return;
	}

	var curAnim = this._sprite.animations.name;

	// The frame names are ???/001.png etc.
	var start = this._sprite.frameName.length - 7;
	var frameNumber = this._sprite.frameName.substring(start, start + 3);
	var curFrame = parseInt(frameNumber);

	this._multiSprites.forEach(function(ms) {

		// Get the current sub sprite anim name.
		var subPos = this._getSubspriteOffset(ms.name, curAnim, curFrame);

		ms.position = {
			x : ms.bestiaDefaultAnchor.x + subPos.x,
			y : ms.bestiaDefaultAnchor.y + subPos.y
		};

	}, this);
};
