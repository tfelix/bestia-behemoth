
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

        this._data[desc.name] = {
            data: desc,
            availableAnimationNames: this._getAnimationNames(desc)
        };
    }

    getSpriteDescription(spriteKey) {
        return this._data[spriteKey].data;
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

    getOffsetDescription(spriteName, multisprite) {
        throw 'implement';
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
     * @param {*} spriteName 
     * @param {*} animatioName 
     * @return {boolean} TRUE if the sprite has this animation. FALSE otherwise.
     */
    hasAnimation(spriteName, animatioName) {
        if (animatioName.indexOf('right') !== -1) {
            animatioName = animatioName.replace('right', 'left');
        }

        return true;
        //return _availableAnimationNames.indexOf(name) !== -1;
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