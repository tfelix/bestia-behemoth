import Renderer from './Renderer';
import LOG from '../../util/Log';
import { spriteCache, entityCache } from '../EngineData';
import { MovementTrait } from '../entities/traits/MovementTrait';
import { VisualTrait } from '../entities/traits/VisualTrait';


/**
 * Synchronizes the entity sprite position with the current sprite position of the game engine.
 */
export default class EntityRenderer extends Renderer {

    constructor(game) {
        super();

        this.traits = [];
        this.traits.push(new VisualTrait(game))
        this.traits.push(new MovementTrait(game));
    }

    get name() {
        return 'entity';
    }

    isDirty() {
        // Currently we render each step.
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
            this.traits.forEach(function (trait) {
                if (trait.hasTrait(entity)) {
                    // Iterate over all entities and try to get the sprite and
                    // check the current animation, position, movement etc.
                    var sprite = spriteCache.getSprite(entity.eid);
                    trait.handleTrait(entity, sprite);
                }
            }, this);
        }, this);
    }
}