
/**
 * Associate certain entities with an animation.
 */
class AnimationCache {

    constructor() {

    }

    /**
     * Checks if a certain animation name is associated with a sprite. 
     * It gets a bit complicated since some animations are implemented 
     * purely in software (right walking a mirror of left walking).
     * @param {*} spriteName 
     * @param {*} animatioName 
     * @return {boolean} TRUE if the sprite has this animation. FALSE otherwise.
     */
    hasAnimationName(spriteName, animatioName) {
        if (name.indexOf('right') !== -1) {
            name = name.replace('right', 'left');
        }
    
        return _availableAnimationNames.indexOf(name) !== -1;
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
}