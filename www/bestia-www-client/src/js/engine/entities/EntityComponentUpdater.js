import MID from '../../io/messages/MID';

/**
 * This class takes incoming component updates and syncs the entity model with this 
 * data to keep it in sync with the server.
 * The engine should access this information to render the entities.
 */
export default class EntityComponentUpdater {

	constructor(pubsub, entityCache) {

		this._entityCache = entityCache;
		this._dirtyEntityIds = [];

		pubsub.subscribe(MID.ENTITY_COMPONENT_UPDATE, this._onComponentUpdate, this);
		pubsub.subscribe(MID.ENTITY_COMPONENT_DELETE, this._onComponentDelete, this);
	}

    /**
     * Called if an update for an entity with its component is send.
     */
	_onComponentUpdate(_, msg) {
		let e = this._getOrCreateEntity(msg.eid);

		// Check if the component differs from each other.
		if (this._isComponentDataEqual(e.components[msg.ct], msg.c)) {
			return;
		}

		e.components[msg.ct] = msg.c;
		this._dirtyEntityIds.push(msg.eid);
	}

    /**
     * Called if an component was deleted from the system.
     */
	_onComponentDelete(_, msg) {
		let e = this._getOrCreateEntity(msg.eid);
		if (e.components.hasOwnProperty(msg.cid)) {
			// Mark the component as deleted so the renderer can remove it
			// in the next pass.
			e.componentsDeleted[msg.cid] = e.components[msg.cid];
			delete e.components[msg.cid];
			this._dirtyEntityIds.push(msg.eid);
		}
	}

	/**
	 * Checks if two component objects are the same.
	 */
	_isComponentDataEqual(lhs, rhs) {
		// Cheap trick but our objects remain the order of properties
		// thus this works.
		JSON.stringify(lhs) === JSON.stringify(rhs);
	}

	_getOrCreateEntity(eid) {
		let entity = this._entityCache.getEntity(eid);

		if (!entity) {
			this._entityCache.addEntity(eid);
			entity = this._entityCache.getEntity(eid);
		}

		if (!entity.components) {
			entity.components = {};
			entity.isDirty = true;
		}

		return entity;
	}

	/**
	 * 
	 */
	getDirtyEntityIds() {
		return this._dirtyEntityIds;
	}

	resetDirtyEntityIds() {
		this._dirtyEntityIds = [];
	}
}
