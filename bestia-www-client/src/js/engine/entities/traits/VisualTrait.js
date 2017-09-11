import LOG from '../../../util/Log';
import Trait from './Trait';
import EntityFactory from '../factory/EntityFactory';
import { spriteCache } from '../../EngineData';

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

export function addSubsprite(sprite, subsprite, msData) {
    if (!sprite.hasOwnProperty('_subsprites')) {
        sprite._subsprites = [];
    }
    // Hold ref to subsprite in own counter so we can faster
    // iterate over all added subsprites.
    sprite._subsprites.push(subsprite);
    sprite.addChild(subsprite);

    // Save the multisprite data to the phaser sprite.
    // maybe we can centralize this aswell.
    subsprite._subspriteData = msData;
}

export class VisualTrait extends Trait {

    constructor(game) {
        super();

        if(!game) {
            throw 'game can not be null.';
        }

        this._game = game;
        this._entityFactory = new EntityFactory(game);
    }

    hasTrait(entity) {
        return entity.hasOwnProperty('sprite');
    }

    handleTrait(entity, sprite) {
        if (!sprite) {
            // We have no sprite. Maybe we need to create it.
            if (entity.action === 'appear') {
                this.buildEntitySprite(entity);
                entity.action = null;
            }
        } else {
            this.checkSpriteRender(entity, sprite);
        }
    }

    buildEntitySprite(entity) {
        // Build the display object and attach it to the sprite cache.
        this._entityFactory.build(entity, function (displayObj) {

            if (displayObj) {
                LOG.debug('Adding sprite to sprite cache: ' + entity.sprite.name);
                spriteCache.setSprite(entity.eid, displayObj);
                displayObj.alpha = 0;

                // Fade in the entity.
                this._game.add.tween(displayObj).to({ alpha: 1 }, 500, Phaser.Easing.Linear.None, true);

                // Check if it needs rendering.
                this.checkSpriteRender(entity, displayObj);
            }

        }.bind(this));
    }

    /**
     * Checks if some render operations are waiting to be executed for this sprite.
     * 
     * @param {object} entity 
     * @param {PhaserJS.Sprite} sprite 
     */
    checkSpriteRender(entity, sprite) {

        this.tickEntityAnimation(entity, sprite);

        if (entity.hasOwnProperty('animation')) {
            if (isMultisprite(sprite)) {
                playSubspriteAnimation(sprite, entity.animation);
            } else {
                sprite.animations.play(entity.animation);
            }
        }
    }

    tickEntityAnimation(entity, sprite) {

    }
}