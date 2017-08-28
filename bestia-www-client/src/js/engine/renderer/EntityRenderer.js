import Renderer from './Renderer';
import entityCache from '../entities/EntityCacheEx';
import LOG from '../../util/Log';
import spriteCache from '../PhaserSpriteCache';

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
        // no op
    }

    update() {
        entityCache.getAllEntities().forEach(function(entity){
            var sprite = spriteCache.getSprite(entity.eid);
            
            if(sprite) {
                sprite.x = entity.x;
                sprite.y = entity.y;
            } else {
                LOG.warn('Could not find sprite for entity: ' + JSON.stringify(entity));
            }
        });
    }
}