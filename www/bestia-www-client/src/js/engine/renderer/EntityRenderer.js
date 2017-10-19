import Renderer from './Renderer';
import LOG from '../../util/Log';
import { spriteCache, entityCache, engineContext } from '../EngineData';
import { MovementTrait } from '../entities/traits/MovementTrait';
import { VisualTrait } from '../entities/traits/VisualTrait';
import { ChatTrait } from '../entities/traits/ChatTrait';


/**
 * Synchronizes the entity sprite position with the current sprite position of the game 
 * engine. Since an entity holds some very complex data structures the renderer is broken 
 * apart in differnt sub renderer which are responsible for single traits of an entity.
 */
export default class EntityRenderer extends Renderer {

    constructor(game) {
        super();

        this.traits = [];
        this.traits.push(new VisualTrait(game))
        this.traits.push(new MovementTrait(game));
        this.traits.push(new ChatTrait(game, engineContext.pubsub));
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

    load(game) {
        game.load.image('default_item', engineContext.url.getItemIconUrl('_default'));
    }

    /**
     * Iterates through every entity and checks if there is render work to be done
     * to display stuff attached to this entity.
     */
    update() {
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