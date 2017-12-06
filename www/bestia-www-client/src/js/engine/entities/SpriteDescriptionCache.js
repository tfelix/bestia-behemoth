import LOG from '../../util/Log';

const OFFSET_ZERO = Object.freeze({ x: 0, y: 0 });

/**
 * This class holds globally all sprite animation data of instanced sprites. It should be asked
 * if a sprite wants to perform an animation this cache is asked if the animation does exist.
 * If the sprite description is loaded the first time the description object is feed into the 
 * SpriteAnimationCache and the data is parsed into a usable format.
 * If there is an animation requested which does not exist it will return an alternative usable
 * animation name.
 */
export default class SpriteDescriptionCache {

	constructor() {

		// Setup data.
		this.clear();
	}

	/**
	 * Adds the given sprite description to the manager and parses its data to a better usable
	 * format for the manager.
	 * 
	 * @public
	 * @param {object} desc - Description for the sprite object.
	 */
	addSpriteDescription(desc) {

		LOG.debug('Adding sprite desc for: ' + desc.name);

		this._data[desc.name] = {
			data: desc,
			availableAnimationNames: this._getAnimationNames(desc)
		};
	}

	addSubspriteOffset(subspriteName, subDesc) {
		if (!subDesc || !subspriteName) {
			LOG.warn('Subsprite name and subsprite description must be given.');
		}

		LOG.debug('Adding subsprite desc for: ' + subspriteName);

		// We transform the data a little to make lookups easier.
		var transformedSubDesc = {
			defaultCords: subDesc.defaultCords
		};

		subDesc.offsets.forEach(function (offset) {
			transformedSubDesc[offset.triggered] = {
				animationName: offset.name,
				offsets: offset.offsets
			};
		}, this);

		// Build the tree.
		this._offsetData[subDesc.targetSprite] = {};
		this._offsetData[subDesc.targetSprite][subspriteName] = transformedSubDesc;
	}

	getSpriteDescription(spriteKey) {
		if (!this.hasSpriteDescription(spriteKey)) {
			return null;
		} else {
			return this._data[spriteKey].data;
		}
	}

    /**
     * Check if the description for this sprite was already loaded.
     * 
     * @param {string} spriteName Name of the sprite to check for its description.
     */
	hasSpriteDescription(spriteName) {
		return this._data.hasOwnProperty(spriteName);
	}

	/**
	 * Extracts the available animation names from a description data file.
	 */
	_getAnimationNames(data) {
		return data.animations.map(function (val) {
			return val.name;
		});
	}

	getRandomStandAnimation(spriteName) {
		// Find all animations in which it stands.
		var standAnimations = this._data[spriteName].animations.filter(function (anim) {
			return anim.name.indexOf('stand') !== -1;
		});

		// Pick a custom one.
		var i = Math.floor(Math.random() * standAnimations.length);
		return standAnimations[i];
	}

    /**
     * Returns the current offset for the given subsprite, animation of the main
     * sprite and current animation frame.
     * 
     * @param {string} subsprite
     *            Name of the subsprite to look for its anchor offset.
     * @param {string} currentAnim
     *            Currently running animation of the main sprite.
     * @param {number} currentFrame
     *            The current frame of the main sprite. Note that frame numbers start with 1.
     * @returns
     */
	getSubspriteOffset(parentSprite, subsprite, currentAnim, currentFrame) {

		if (!this._offsetData.hasOwnProperty(parentSprite)) {
			// No subsprite data present.
			return OFFSET_ZERO;
		}

		var data = this._offsetData[parentSprite];

		if (!data.hasOwnProperty(subsprite)) {
			return OFFSET_ZERO;
		}

		data = data[subsprite];

		if (!data.hasOwnProperty(currentAnim)) {
			return data.defaultCords || OFFSET_ZERO;
		}

		data = data[currentAnim];

		if (data.offsets.length < currentFrame) {
			return data.defaultCords || OFFSET_ZERO;
		}

		return data.offsets[currentFrame - 1];
	}

    /**
     * Returns the assoziated subsprite animation name. 
     * Or 'bottom.png' if no animation was found which is the default 'look
     * down' animation.
     * 
     * @param {string} subspriteName Name of the subsprite.
     * @param {string} animName Name of the animation which is played on the main sprite.
     * @param {string} spriteName Name of the main sprite.
     */
	getSubspriteAnimation(spriteName, subspriteName, animName) {
		if (!this._offsetData.hasOwnProperty(spriteName)) {
			// No subsprite data present.
			return 'bottom';
		}

		var data = this._offsetData[spriteName];

		if (!data.hasOwnProperty(subspriteName)) {
			return 'bottom';
		}

		data = data[subspriteName];

		if (!data.hasOwnProperty(animName)) {
			return 'bottom';
		}

		data = data[animName];

		return data.animationName || 'bottom';
	}

    /**
     * Tries to find a alternate animation. If no supported animation could be
     * found, we will return null.
     * 
     * @private
     * @return {String} - Newly found animation to display, or NULL if no
     *         suitable animation could been found.
     */
	getAnimationFallback(name) {
		if (name === 'stand_down_left' || name === 'stand_left') {
			// Try to replace it with stand down.
			if (this._availableAnimationNames.indexOf('stand_down') === -1) {
				return null;
			} else {
				return 'stand_down';
			}
		}

		if (name == 'stand_left_up') {
			// Try to replace it with stand down.
			if (this._availableAnimationNames.indexOf('stand_up') === -1) {
				return null;
			} else {
				return 'stand_up';
			}
		}

		if (name == 'stand_right') {
			// Try to replace it with stand down.
			if (this._availableAnimationNames.indexOf('stand_up') === -1) {
				return null;
			} else {
				return 'stand_up';
			}
		}

		return null;
	}

    /**
     * Checks if a certain animation name is associated with a sprite. 
     * It gets a bit complicated since some animations are implemented 
     * purely in software (right walking a mirror of left walking).
     * @param {string} spriteName The name of the sprite 
     * @param {string} animatioName The animation name to check if the sprite has it.
     * @return {boolean} TRUE if the sprite has this animation. FALSE otherwise.
     */
	hasAnimation(spriteName, animatioName) {
		if (animatioName.indexOf('right') !== -1) {
			animatioName = animatioName.replace('right', 'left');
		}

		if (!this._data.hasOwnProperty(spriteName)) {
			return false;
		}

		return this._data[spriteName].availableAnimationNames.indexOf(animatioName) !== -1;
	}

	/**
	 * Clears and resets the cache in its original state thus deleting all the cached data.
	 * 
	 * @public
	 */
	clear() {
		this._data = {};
		this._offsetData = {};
	}
}