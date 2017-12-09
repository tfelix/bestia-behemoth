import * as Phaser from 'phaser';
import ComponentNames from '../../entities/ComponentNames';

/**
 * This will play the entity animation in the next tick of the render system.
 * 
 * @export
 * @param {any} entity The entity to play the animation.
 * @param {any} animName The animation to play for this entity.
 */
export function playEntityAnimation(entity, animName) {
	if (!entity.components[ComponentNames.VISIBLE]) {
		entity.components[ComponentNames.VISIBLE] = {};
	}
	entity.components[ComponentNames.VISIBLE].nextAnimation = animName;
}

/**
 * Helper function to setup a sprite with all the information contained
 * inside a description object.
 * 
 * @param sprite
 * @param {String} sprite - Name of the sprite to be setup.
 */
export function setupSpriteAnimation(sprite, description) {

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

export function addSubsprite(sprite, subsprite) {
	if (!sprite.hasOwnProperty('_subsprites')) {
		sprite._subsprites = [];
	}
	// Hold ref to subsprite in own counter so we can faster
	// iterate over all added subsprites.
	sprite._subsprites.push(subsprite);
	sprite.addChild(subsprite);
}