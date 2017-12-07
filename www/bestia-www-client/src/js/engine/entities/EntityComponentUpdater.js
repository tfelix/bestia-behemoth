import MID from '../../io/messages/MID';
import LOG from '../../util/Log';
import Signal from '../../io/Signal';
import PositionCompTranslator from './PositionCompTranslator';
import StatusComponentTranslator from './StatusComponentTranslator';
import VisualComponentTranslator from './VisualComponentTranslator';
import PlayerComponentTranslator from './PlayerComponentTranslator';
import LevelComponentTranslator from './LevelComponentTranslator';

/**
 * This class takes incoming component updates and syncs the entity model with this 
 * data to keep it in sync with the server.
 * The engine should access this information to render the entities.
 */
export default class EntityComponentUpdater {

	constructor(pubsub, entityCache) {

		this._entityCache = entityCache;

		this._translators = [];
		this._translators.push(new PositionCompTranslator());
		this._translators.push(new StatusComponentTranslator());
		this._translators.push(new VisualComponentTranslator());
		this._translators.push(new PlayerComponentTranslator());
		this._translators.push(new LevelComponentTranslator());

		this._pubsub = pubsub;

		pubsub.subscribe(MID.ENTITY_COMPONENT_UPDATE, this._onComponentUpdate, this);
		pubsub.subscribe(MID.ENTITY_COMPONENT_DELETE, this._onComponentDelete, this);
	}

    /**
     * Called if an update for an entity with its component is send.
     */
	_onComponentUpdate(_, msg) {

		let transMsg = null;

		// Try to translate the message into a usable format.
		this._translators.forEach(trans => {
			if (trans.handlesComponent(msg)) {
				transMsg = trans.translate(msg);
			}
		});

		if (transMsg === null) {
			LOG.warn('Component message could not be translated.');
			return;
		}

		let entity = this._getOrCreateEntity(transMsg.eid);

		// Check if the component differs from each other.
		let component = entity.components[transMsg.type];
		if (this._isComponentDataEqual(component, transMsg)) {
			return;
		}

		entity.components[transMsg.type] = transMsg;
		this._pubsub.publish(Signal.ENTITY_UPDATE, entity, component);
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
		}

		return entity;
	}
}
