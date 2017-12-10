import Renderer from './Renderer';
import Signal from '../../io/Signal';
import { MovementTrait } from './traits/MovementTrait';
import { VisualTrait } from './traits/VisualTrait';
import { ChatTrait } from './traits/ChatTrait';


/**
 * Synchronizes the entity sprite position with the current sprite position of the game 
 * engine. Since an entity holds some very complex data structures the renderer is broken 
 * apart in differnt sub renderer which are responsible for single traits of an entity.
 */
export default class EntityRenderer extends Renderer {

	constructor(pubsub, game, spriteCache, url) {
		super();

		this._updatedEntitites = [];
		this._url = url;
		this._spriteCache = spriteCache;

		this.traits = [];
		this.traits.push(new VisualTrait(game));
		this.traits.push(new MovementTrait(game, pubsub));
		this.traits.push(new ChatTrait(game, pubsub));

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

	/**
	 * If the entity was changed we need to update the entity inside the queue to 
	 * the latest version and issue an update.
	 */
	_handleEntityUpdate(_, entity) {
		// We must check if we have an entity with this id already in queue.
		let index = this._updatedEntitites.findIndex(v => v.eid === entity.eid);

		if(index !== -1) {
			this._updatedEntitites.splice(index, 1);
		}

		// Add the new entity.
		this._updatedEntitites.push(entity);
	}

	load(loader) {
		loader.image('default_item', this._url.getItemIconUrl('_default'));
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
					var sprite = this._spriteCache.getSprite(entity.eid);
					trait.handleTrait(entity, sprite);
				}

				// Call some cleanup code if needed.
				trait.postEntityIteration();
			}, this);
		}, this);

		this._updatedEntitites = [];
	}
}