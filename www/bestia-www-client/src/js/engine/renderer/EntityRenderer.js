import Renderer from './Renderer';
import Signal from '../../io/Signal';
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

	constructor(game, pubsub) {
		super();

		this._updatedEntitites = [];

		this.traits = [];
		this.traits.push(new VisualTrait(game));
		this.traits.push(new MovementTrait(game));
		this.traits.push(new ChatTrait(game, engineContext.pubsub));

		//this._compUpdater = engineContext.entityComponentUpdater;
		pubsub.subscribe(Signal.ENTITY_UPDATE, this._handleEntityUpdate, this);
	}

	get name() {
		return 'entity';
	}

	isDirty() {
		return this._updatedEntitites.length > 0;
	}

	clear() {
		// no op
	}

	_handleEntityUpdate(_, entity) {
		this._updatedEntitites.push(entity);
	}

	load(game) {
		game.load.image('default_item', engineContext.url.getItemIconUrl('_default'));
	}

    /**
     * Iterates through every entity and checks if there is render work to be done
     * to display stuff attached to this entity.
     */
	update() {
		
		this._updatedEntitites.forEach(function (entity) {
			
			this.traits.forEach(function (trait) {
				if (trait.hasTrait(entity)) {
					// Iterate over all entities and try to get the sprite and
					// check the current animation, position, movement etc.
					var sprite = spriteCache.getSprite(entity.eid);
					trait.handleTrait(entity, sprite);
				}
			}, this);
		}, this);

		this._updatedEntitites = [];
	}
}