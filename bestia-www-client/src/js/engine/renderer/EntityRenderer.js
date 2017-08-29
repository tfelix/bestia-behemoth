import Renderer from './Renderer';
import entityCache from '../entities/EntityCacheEx';
import LOG from '../../util/Log';
import spriteCache from '../PhaserSpriteCache';
import EntityFactory from './../entities/factory/EntityFactory';

/**
 * Synchronizes the entity sprite position with the current sprite position of the game engine.
 */
export default class EntityRenderer extends Renderer {

    /**
     * 
     */
    constructor(ctx) {
        super();

        if(!ctx) {
            throw 'Context can not be null.';
        }

        this._entitySprites = {};
        this._entityFactory = new EntityFactory(ctx);
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

    update() {
        entityCache.getAllEntities().forEach(function (entity) {
            var sprite = spriteCache.getSprite(entity.eid);

            if (!sprite) {
                // We have no sprite. Maybe we need to create it.
                if (entity.action === 'appear') {
                    this.buildEntitySprite(entity);
                    entity.action = null;
                }
            } else {
                sprite.x = entity.x;
                sprite.y = entity.y;
            }
        });
    }

    buildEntitySprite(entity) {
        // Build the display object and attach it to the sprite cache.
        this._entityFactory.build(entity, function (displayObj) {

            spriteCache.setSprite(entity.eid, displayObj);
            /*
            if (msg.eid === this._ctx.playerBestia.entityId()) {
                spriteCache.setPlayerSprite(displayObj);
            }*/
        }.bind(this));
    }
}