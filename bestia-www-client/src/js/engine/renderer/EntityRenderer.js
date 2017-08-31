import Renderer from './Renderer';
import LOG from '../../util/Log';
import { spriteCache, entityCache } from '../EngineData';
import EntityFactory from './../entities/factory/EntityFactory';

/**
 * Synchronizes the entity sprite position with the current sprite position of the game engine.
 */
export default class EntityRenderer extends Renderer {

    /**
     * 
     */
    constructor() {
        super();

        this._entityFactory = new EntityFactory();
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
                this.tickEntityAnimation(entity, sprite);
            }
        }, this);
    }

    tickEntityAnimation(entity, sprite) {
        // TODO Das hier implementieren und aus entity raus holen.
        //entity.tickAnimation();
    }

    buildEntitySprite(entity) {
        // Build the display object and attach it to the sprite cache.
        this._entityFactory.build(entity, function (displayObj) {

            spriteCache.setSprite(entity.eid, displayObj);

        }.bind(this));
    }
}