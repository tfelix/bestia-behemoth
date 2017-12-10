import LOG from '../../../util/Log';
import * as Phaser from 'phaser';
import Trait from './Trait';
import EntityFactory from '../../entities/factory/EntityFactory';
import ComponentNames from '../../entities/ComponentNames';

export class VisualTrait extends Trait {

	constructor(ctx) {
		super();

		if (!ctx) {
			throw 'Context can not be null.';
		}

		this._game = ctx.game;
		this._spriteCache = ctx.spriteCache;
		this._descCache = ctx.descriptionCache;
		this._entityFactory = new EntityFactory(ctx.game);
	}

	hasTrait(entity) {
		return entity.components.hasOwnProperty(ComponentNames.VISIBLE);
	}

	handleTrait(entity, sprite) {
		if (!sprite) {
			// We have no sprite. Maybe we need to create it.
			this.buildEntitySprite(entity);
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

			let visComp = entity.components[ComponentNames.VISIBLE];
			LOG.debug('Created sprite: ' + visComp.visual.sprite);
			this._spriteCache.setSprite(entity.eid, displayObj);

			displayObj.alpha = 0;

			// Fade in the entity.
			this._game.add.tween(displayObj).to({ alpha: 1 }, 500, Phaser.Easing.Linear.None, true);

			// Switch to idle animation.
			entity.nextAnimation = 'stand_down';

			this._checkSpriteRender(entity, displayObj);

		}.bind(this));
	}

    /**
     * Checks if some render operations are waiting to be executed for this sprite.
     * 
     * @param {object} entity 
     * @param {Sprite} sprite 
     */
	_checkSpriteRender(entity, sprite) {

		let nextAnim = entity.components[ComponentNames.VISIBLE].nextAnimation;
		if (nextAnim) {
			this._playAnimation(sprite, entity, nextAnim);
			delete entity.components[ComponentNames.VISIBLE].nextAnimation;
		}

		// If this is a multisprite also change the animation of the subsprites.
		if (this._isMultisprite(sprite)) {
			this._tickSubspriteAnimation(sprite);
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
		let desc = this._descCache.getSpriteDescription(sprite.key);

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
		if (!this._descCache.hasAnimation(sprite.key, animName)) {

			animName = this._descCache.getAnimationFallback(animName);

			if (animName === null) {
				LOG.warn('Could not found alternate animation solution for: ' + name);
				return;
			}
		}

		this._performLeftRightFlip(sprite, animName);

		if (this._isMultisprite(sprite)) {
			this._playSubspriteAnimation(sprite, entity.nextAnimation);
		}
	}

	_playSubspriteAnimation(sprite, animName) {
		// Iterate over all subsprites an set their animations.
		sprite._subsprites.forEach(function (subsprite) {

			animName = this._performLeftRightFlip(sprite, animName);

			let subAnim = this._descCache.getSubspriteAnimation(sprite.key, subsprite.key, animName);
			subsprite.frameName = subAnim;

		}, this);
	}

    /**
     * Update any subsprite animation with the current subsprite offset postion.
     */
	_tickSubspriteAnimation(sprite) {

		var curAnim = sprite.animations.name;

		// The frame names are ???/001.png etc.
		if (sprite.frameName === undefined) {
			LOG.error('Unknown subsprite name. Should not happen.');
		}
		var start = sprite.frameName.length - 7;
		var frameNumber = sprite.frameName.substring(start, start + 3);
		var curFrame = parseInt(frameNumber, 10);

		sprite._subsprites.forEach(function (subSprite) {

			// Get the current sub sprite anim name.
			let subPos = this._descCache.getSubspriteOffset(sprite.key, subSprite.key, curAnim, curFrame);
			subSprite.position.x = subPos.x;
			subSprite.position.y = subPos.y;

		}, this);
	}
}