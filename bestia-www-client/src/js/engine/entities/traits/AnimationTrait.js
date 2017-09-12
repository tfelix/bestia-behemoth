import Trait from './Trait';

function addEntityAnimation(entity, animName) {
    entity.nextAnimation = animName;
}

/**
 * Plays a specific animation. If it is a walk animation then by the name of
 * the animation the method takes core of flipping the sprite for mirrored
 * animations. It also handles stopping running animations and changing
 * between them.
 * 
 * @public
 * @param {String} name - Name of the animation to play.
 */
function playAnimation(sprite, entityData, name) {

    LOG.debug('Playing animation: ' + name);

    // If the animation is already playing just leave it.
    if (name === sprite.animations.name) {
        return;
    }

    // Check if the sprite 'knows' this animation. If not we have several
    // fallback strategys to test before we fail.
    if (!_hasAnimationName(name)) {
        name = _getAnimationFallback(name);

        if (name === null) {
            LOG.warn('Could not found alternate animation solution for: ' + name);
            return;
        }
    }

    // We need to mirror the sprite for right sprites.
    if (name.indexOf('right') !== -1) {
        sprite.scale.x = -1 * this._data.scale;
        name = name.replace('right', 'left');
    } else {
        sprite.scale.x = this._data.scale;
    }

    sprite.play(name);
}

/**
 * Renders sprite animations and makes sure the sprite contains this 
 * animation.
 */
export class AnimationTrait extends Trait {

    constructor() {
        super();
    }

    /**
     * Checks if the given entity contains the movement trait.
     * @param {object} entity Entity object. 
     */
    hasTrait(entity) {
        return entity.hasOwnProperty('nextAnimation');
    }

    /**
     * Handle the event if a entity has a special trait attached to its datastructure.
     * @param {object} entity Entity object describing the entity.
     * @param {PhaserJS.Sprite} sprite Sprite object from PhaserJS.
     */
    handleTrait(entity, sprite) {
        LOG.info('Animation is played');
    }
}