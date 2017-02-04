import SpriteEntity from './SpriteEntity.js';

const NULL_OFFSET = {x: 0, y: 0};

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
export default class MultispriteEntity extends SpriteEntity {
	constructor(game, id, desc) {
		super(game, id, -100, -100, desc);
		
		/**
		 * Contains information about all multi sprites added to this sprite. It
		 * contains: {sprite: Phaser.Sprite, name: String, offsets: Obj,
		 * defaultCords: {x:, y:}}
		 */
		this._multiSprites = [];
	}

	getOffsetFilename(multispriteName, mainspriteName) {
		return 'offset_' + multispriteName + '_' + mainspriteName;
	}

	/**
	 * Returns the name of the subsprite animation depending of the current
	 * 'main' animation running on the main sprite. Can be used to set the
	 * subsprite animations after the animation of the main sprite has changed.
	 * 
	 * @param {string}
	 *            subspriteName Name of the current subsprite.
	 * @param currentAnim
	 * @returns Name of the subsprite animation.
	 */
	_getSubspriteAnimation(subspriteName, currentAnim) {
		
		let subsprite = this._getSubspriteData(subspriteName);
		
		if(subsprite === null) {
			return null;
		}
	
		for (var i = 0; i < subsprite.offsets.length; i++) {
			if (subsprite.offsets[i].triggered === currentAnim) {
				return subsprite.offsets[i].name;
			}
		}
	
		// No anim found.
		return null;
	}
	
	/**
	 * Searches for the data of the subsprite.
	 */
	_getSubspriteData(subspriteName) {
		for (let i = 0; i < this._multiSprites.length; i++) {
			if (this._multiSprites[i].name === subspriteName) {
				return this._multiSprites[i];
			}
		}
		return null;
	}

	/**
	 * Sets the sprite of the entity. TODO Das hier alles in die Factory
	 * auslagern. Das Bauen der Sprites.
	 * 
	 * @param {string}
	 *            spriteName - New name of the sprite.
	 */
	setSprite(spriteName) {
		super.setSprite(spriteName);
		
		console.log('setSprite called ' + spriteName);
	
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
	
			let anchor = msDesc.anchor;
			let sprite = this._game.make.sprite(anchor.x, anchor.y, msName);
			this._sprite.addChild(sprite);
			
			sprite.anchor = anchor;
	
			// TODO This should be automatically parsed.
			// Setup the normal data.
			sprite.scale.setTo(msDesc.scale || 1);
			sprite.animations.add('bottom.png', [ 'bottom.png' ], 0, true, false);
			sprite.animations.add('bottom_left.png', [ 'bottom_left.png' ], 0, true, false);
			sprite.animations.add('left.png', [ 'left.png' ], 0, true, false);
			sprite.animations.add('left.png', [ 'left.png' ], 0, true, false);
			sprite.animations.add('top.png', [ 'top.png' ], 0, true, false);
			sprite.animations.add('top_left.png', [ 'top_left.png' ], 0, true, false);
	
			// Generate offset information.
			let offsetFileName = this.getOffsetFilename(msName, this._data.name);
			let offsets = this._game.cache.getJSON(offsetFileName) || {};
			
			// Prepare the info object.
			let defaultCords = offsets.defaultCords || {
				x : 0,
				y : 0
			};
			let msData = {
					sprite: sprite, 
					offsets: offsets.offsets || [], 
					name: msName,
					defaultCords: defaultCords
			};

			sprite.name = msName;
	
			this._multiSprites.push(msData);
		}, this);
	
		// After setting the subsprites we must manually call set
		this._playSubspriteAnimation(this._sprite.animations.currentAnim.name);
	}

	/**
	 * Returns the current offset for the given subsprite, animation of the main
	 * sprite and current animation frame.
	 * 
	 * @param subsprite
	 *            Name of the subsprite to look for its anchor offset.
	 * @param currentAnim
	 *            Currently running animation of the main sprite.
	 * @param currentFrame
	 *            The current frame of the main sprite.
	 * @returns
	 */
	_getSubspriteOffset(subsprite, currentAnim, currentFrame) {
		
		let subData = this._getSubspriteData(subsprite);
		
		for (let i = 0; i < subData.offsets.length; i++) {
			if(subData.offsets[i].triggered !== currentAnim) {
				continue;
			}

			if(subData.offsets[i].offsets.length > currentFrame) {
				return subData.offsets[i].offsets[currentFrame];
			} else {
				console.warn('getSubspriteOffset: Not enough frames found for: ' + subsprite + ' currentAnim: ' + currentAnim);
				return subData.defaultCords;
			}
		}
		
		// If nothing found return default.
		if(!subData.defaultCords) {
			console.warn('getSubspriteOffset: No default cords found for: ' + subsprite + ' currentAnim: ' + currentAnim);
			return NULL_OFFSET;
		}
		
		return subData.defaultCords;
	}

	/**
	 * It will keep all the subsprites with their animation in sync when the
	 * parent animation was set.
	 * 
	 * @param name
	 *            Name of the new animation to play.
	 */
	playAnimation(name) {
		super.playAnimation(name);
		this._playSubspriteAnimation(name);
	}

	/**
	 * Helper function since this must be called from multiple places.
	 * 
	 * @param mainAnimName
	 */
	_playSubspriteAnimation(mainAnimName) {
		// Iterate over all subsprites an set their animations.
		this._multiSprites.forEach(function(s) {
			let subAnim = this._getSubspriteAnimation(s.name, mainAnimName);
			if (subAnim === null) {
				// no suitable sub animation found. Do nothing.
				return;
			}
			s.sprite.play(subAnim);
	
		}, this);
	}

	/**
	 * Depending on current animation update the sprite offset.
	 */
	tickAnimation() {
		if (this._sprite === null) {
			return;
		}
	
		var curAnim = this._sprite.animations.name;
	
		// The frame names are ???/001.png etc.
		if(this._sprite.frameName === undefined) {
			console.error('Soll nicht passieren');
		}
		var start = this._sprite.frameName.length - 7;
		var frameNumber = this._sprite.frameName.substring(start, start + 3);
		var curFrame = parseInt(frameNumber, 10);
	
		this._multiSprites.forEach(function(ms) {
	
			// Get the current sub sprite anim name.
			let subPos = this._getSubspriteOffset(ms.name, curAnim, curFrame);
	
			ms.sprite.position = {
				x : subPos.x,
				y : subPos.y
			};
	
		}, this);
	}
}