import LOG from '../../../util/Log';
import Trait from './Trait';
import EntityFactory from '../factory/EntityFactory';
import { spriteCache, descriptionCache } from '../../EngineData';
import ComponentNames from '../ComponentNames';

export function addEntityAnimation(entity, animName) {
	entity.nextAnimation = animName;
}

/**
 * Helper function to setup a sprite with all the information contained
 * inside a description object.
 * 
 * @param sprite
 * @param {String} spriteName - Name of the sprite to be setup.
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

export class VisualTrait extends Trait {

	constructor(game) {
		super();

		if (!game) {
			throw 'game can not be null.';
		}

		this._game = game;
		this._entityFactory = new EntityFactory(game);
	}

	hasTrait(entity) {
		return entity.components.hasOwnProperty(ComponentNames.VISIBLE);
	}

	handleTrait(entity, sprite) {
		if (!sprite) {
			// We have no sprite. Maybe we need to create it.
			if (entity.action === 'appear') {
				this.buildEntitySprite(entity);
				entity.action = null;
			}
		} else {
			this._checkSpriteRender(entity, sprite);
		}
	}

	buildEntitySprite(entity) {
		// Build the display object and attach it to the sprite cache.
		this._entityFactory.build(entity, function (displayObj) {

			if (!displayObj) {
				LOG.warn('Could not create sprite for entity: ' + JSON.stringify(entity));
				return;
			}

			LOG.debug('Created sprite: ' + entity.sprite.name);
			spriteCache.setSprite(entity.eid, displayObj);

			displayObj.alpha = 0;

			// Fade in the entity.
			this._game.add.tween(displayObj).to({ alpha: 1 }, 500, Phaser.Easing.Linear.None, true);

            /*
            // Check if it needs rendering.
            this._checkSpriteRender(entity, displayObj);*/

			// Switch to idle animation.
			addEntityAnimation(entity, 'stand_down');

		}.bind(this));
	}

    /**
     * Checks if some render operations are waiting to be executed for this sprite.
     * 
     * @param {object} entity 
     * @param {PhaserJS.Sprite} sprite 
     */
	_checkSpriteRender(entity, sprite) {

		if (entity.hasOwnProperty('nextAnimation')) {
			this._playAnimation(sprite, entity, entity.nextAnimation);
		}

		if (this._isMultisprite(sprite)) {
			this._tickSubspriteAnimation(sprite, entity);
		}
	}

    /**
     * Checks if the given sprite is a bestia multisprite.
     */
	_isMultisprite(sprite) {
		return sprite.hasOwnProperty('_subsprites');
	}

	_performLeftRightFlip(sprite, animName) {
		// Get description data of this sprite.
		let desc = descriptionCache.getSpriteDescription(sprite.key);

		// We need to mirror the sprite for right sprites.
		if (animName.indexOf('right') !== -1) {
			sprite.scale.x = -1 * desc.scale;
			animName = animName.replace('right', 'left');
		} else {
			sprite.scale.x = desc.scale;
		}

		sprite.animations.play(animName);
		return animName;
	}

    /**
     * Plays a specific animation. If it is a walk animation then by the name of
     * the animation the method takes core of flipping the sprite for mirrored
     * animations. It also handles stopping running animations and changing
     * between them.
     * 
     * @public
     * @param {String} animName - Name of the animation to play.
     */
	_playAnimation(sprite, entity, animName) {

		LOG.debug('Playing animation: ' + animName + ' for entity: ' + entity.eid);

		// If the animation is already playing just leave it.
		if (animName === sprite.animations.name) {
			return;
		}

		// Check if the sprite 'knows' this animation. If not we have several
		// fallback strategys to test before we fail.
		if (!descriptionCache.hasAnimation(sprite.key, animName)) {

			animName = descriptionCache.getAnimationFallback(sprite.key, animName);

			if (animName === null) {
				LOG.warn('Could not found alternate animation solution for: ' + name);
				return;
			}
		}

		this._performLeftRightFlip(sprite, animName);

		if (this._isMultisprite(sprite)) {
			this._playSubspriteAnimation(sprite, entity.nextAnimation);
		}

		delete entity.nextAnimation;
	}

	_playSubspriteAnimation(sprite, animName) {
		// Iterate over all subsprites an set their animations.
		sprite._subsprites.forEach(function (subsprite) {

			animName = this._performLeftRightFlip(sprite, animName);

			let subAnim = descriptionCache.getSubspriteAnimation(sprite.key, subsprite.key, animName);
			subsprite.frameName = subAnim;

		}, this);
	}

    /**
     * Update any subsprite animation with the current subsprite offset postion.
     */
	_tickSubspriteAnimation(sprite, entity) {

		var curAnim = sprite.animations.name;

		// The frame names are ???/001.png etc.
		if (sprite.frameName === undefined) {
			console.error('Soll nicht passieren');
		}
		var start = sprite.frameName.length - 7;
		var frameNumber = sprite.frameName.substring(start, start + 3);
		var curFrame = parseInt(frameNumber, 10);

		sprite._subsprites.forEach(function (subSprite) {

			// Get the current sub sprite anim name.
			let subPos = descriptionCache.getSubspriteOffset(sprite.key, subSprite.key, curAnim, curFrame);
			subSprite.position.x = subPos.x;
			subSprite.position.y = subPos.y;

		}, this);
	}
}