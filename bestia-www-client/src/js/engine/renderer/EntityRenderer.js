import Renderer from './Renderer';
import entityCache from '../entities/EntityCacheEx';
import LOG from '../../util/Log';

/**
 * Contains a cache holding references to display objects and entity ids which must
 * be used to reference them vice versa.
 */
var phaserSpriteCache = {};

/**
 * Synchronizes the entity sprite position with the current sprite position of the game engine.
 */
export default class EntityRenderer extends Renderer {

    /**
     * 
     */
    constructor() {
        super();

        this._entitySprites = {};
    }

    get name() {
        return 'entity';
    }

    isDirty() {
        return true;
    }

    clear() {
        phaserSpriteCache = {};
    }

    update() {
        entityCache.getAllEntities().forEach(function(entity){
            var sprite = phaserSpriteCache[entity.eid];
            
            if(sprite) {
                sprite.x = entity.x;
                sprite.y = entity.y;
            } else {
                LOG.warn('Could not find sprite for entity: ' + entity);
            }
        });
    }
}

export {phaserSpriteCache};