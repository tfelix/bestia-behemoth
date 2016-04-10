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

Bestia.Engine.MultispriteEntity.getOffsetFilename = function(multispriteName, mainspriteName) {
	return 'offset_' + multispriteName + '_' + mainspriteName;
};

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

	// TODO This works currnetly only if there is only one subsprite. Extend
	// this for true multi sprite support.
	for (var i = 0; i < this._animOffset.length; i++) {
		if (this._animOffset[i].triggered === currentAnim) {
			return this._animOffset[i].name;
		}
	}

	// No anim found.
	return null;
};

/**
 * Sets the sprite of the entity. TODO Das hier alles in die Factory auslagern.
 * Das Bauen der Sprites.
 * 
 * @param {string}
 *            spriteName - New name of the sprite.
 */
Bestia.Engine.MultispriteEntity.prototype.setSprite = function(spriteName) {
	Bestia.Engine.SpriteEntity.prototype.setSprite.call(this, spriteName);

	// Add the multi sprites if there are some of them.
	var multisprites = this._data.multiSprite || [];

	multisprites.forEach(function(msName) {

		// Get the desc file of the multisprite.
		// TODO Das hier vielleicht in die factory auslagern.
		var msDesc = this._game.cache.getJSON(msName + '_desc');

		// Was not loaded. Should not happen.
		if (msDesc == null) {
			return;
		}

		var anchor = msDesc.anchor;

		var sprite = this._game.make.sprite(anchor.x, anchor.y, msName);
		this._sprite.addChild(sprite);
		
		sprite.anchor = anchor;

		// Register all the animations.
		// TODO The head animation names are currently not in the default
		// nameing scheme. need to do this "by hand".
		// this._setupSprite(sprite, msDesc);
		// setupSrite did alpha 0
		// TODO Overwriting appear an letting appear all subsprites would be
		// better.
		// sprite.alpha = 1;

		// Setup the normal data.
		sprite.scale.setTo(msDesc.scale || 1);
		sprite.animations.add("bottom.png", [ "bottom.png" ], 0, true, false);
		sprite.animations.add("bottom_left.png", [ "bottom_left.png" ], 0, true, false);
		sprite.animations.add("left.png", [ "left.png" ], 0, true, false);
		sprite.animations.add("left.png", [ "left.png" ], 0, true, false);
		sprite.animations.add("top.png", [ "top.png" ], 0, true, false);
		sprite.animations.add("top_left.png", [ "top_left.png" ], 0, true, false);

		// Generate offset information.
		var offsetFileName = Bestia.Engine.MultispriteEntity.getOffsetFilename(msName, this._data.name);
		var offsets = this._game.cache.getJSON(offsetFileName) || {};
		this._animOffset = offsets.offsets || [];

		sprite.bestiaDefaultAnchor = offsets.defaultCords || {
			x : 0,
			y : 0
		};

		sprite.name = msName;

		this._multiSprites.push(sprite);
	}, this);

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
		if (subAnim === null) {
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
	var curFrame = parseInt(frameNumber, 10);

	this._multiSprites.forEach(function(ms) {

		// Get the current sub sprite anim name.
		var subPos = this._getSubspriteOffset(ms.name, curAnim, curFrame);

		ms.position = {
			x : ms.bestiaDefaultAnchor.x + subPos.x,
			y : ms.bestiaDefaultAnchor.y + subPos.y
		};

	}, this);
};
