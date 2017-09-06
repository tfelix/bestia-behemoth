import Renderer from './Renderer';
import LOG from '../../util/Log';
import { spriteCache, entityCache } from '../EngineData';
import EntityFactory from './../entities/factory/EntityFactory';
import { entityHasMovement, spriteMovePath } from '../entities/SpriteMovementHelper';
import { isMultisprite, playSubspriteAnimation } from '../entities/MultispriteAnimationHelper';

/**
 * Synchronizes the entity sprite position with the current sprite position of the game engine.
 */
export default class EntityRenderer extends Renderer {

    /**
     * 
     */
    constructor(game) {
        super();

        this._game = game;

        this._entityFactory = new EntityFactory(game);
    }

    get name() {
        return 'entity';
    }

    isDirty() {
        return true;
    }

    clear() {
        // no op
    }

    /**
     * 
     */
    update() {
        // TODO This is a mess. Cleanup.
        entityCache.getAllEntities().forEach(function (entity) {

            // Iterate over all entities and try to get the sprite and
            // check the current animation, position, movement etc.
            var sprite = spriteCache.getSprite(entity.eid);

            if (!sprite) {
                // We have no sprite. Maybe we need to create it.
                if (entity.action === 'appear') {
                    this.buildEntitySprite(entity);
                    entity.action = null;
                }
            } else {
                this.checkSpriteRender(entity, sprite);
            }
        }, this);
    }

    /**
     * Checks if some render operations are waiting to be executed for this sprite.
     * 
     * @param {object} entity 
     * @param {PhaserJS.Sprite} sprite 
     */
    checkSpriteRender(entity, sprite) {

        if (entityHasMovement(entity)) {
            LOG.info('Moving entity: ' + entity.id);

            spriteMovePath(sprite, entity.movement.path, entity.movement.walkspeed);

            delete entity.movement;
        } else {
            //sprite.x = entity.x;
            //sprite.y = entity.y;
            this.tickEntityAnimation(entity, sprite);
        }

        if (entity.animation) {
            if (isMultisprite(sprite)) {
                playSubspriteAnimation(sprite, entity.animation);
            } else {
                sprite.animations.play(entity.animation);
            }
        }
    }

    tickEntityAnimation(entity, sprite) {
        // TODO Das hier implementieren und aus entity raus holen.
        //entity.tickAnimation();
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
}