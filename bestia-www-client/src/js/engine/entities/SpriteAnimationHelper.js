import LOG from '../../util/Log';

/**
 * Helper function to setup a sprite with all the information contained
 * inside a description object.
 * 
 * @param sprite
 * @param {String} spriteName - Name of the sprite to be setup.
 */
function setupSpriteAnimation(sprite, description) {

    // Setup the normal data.
    sprite.anchor = description.anchor || {
        x: 0.5,
        y: 0.5
    };
    
    sprite.scale.setTo(description.scale || 1);

    var anims = description.animations || [];

    LOG.debug('Setup sprite animations:' + JSON.stringify(anims) + ' for: ' + description.name);

    // Register all the animations of the sprite.
    anims.forEach(function (anim) {
        var frames = Phaser.Animation.generateFrameNames(anim.name + '/', anim.from, anim.to, '.png', 3);
        sprite.animations.add(anim.name, frames, anim.fps, true, false);
    }.bind(this));
}

export { setupSpriteAnimation };