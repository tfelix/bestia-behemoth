
/**
 * This class takes incoming component updates and syncs the entity model with this 
 * data to keep it in sync with the server.
 * The engine should access this information to render the entities.
 */
export default class EntityComponentUpdates {

	constructor(pubsub) {

		this._updatedEntities = new Set();


		pubsub.subscribe("entity.comp", this._onComponentUpdate);
		pubsub.subscribe("entity.Del", this._onComponentDelete);
	}

    /**
     * Called if an update for an entity with its component is send.
     */
	_onComponentUpdate(_, msg) {

	}

    /**
     * Called if an component was deleted from the system.
     */
	_onComponentDelete(_, msg) {

	}

	hasUpdatedComponents() {
		return this._updatedEntities.size > 0;
	}

    /**
     * Returns a list with all updated entities since the last call.
     */
	getUpdatedEntities() {

	}

    /**
     * Resets the counter for the updated entities.
     */
	clearUpdatedEntities() {
		this._updatedEntities.clear();
	}
}
