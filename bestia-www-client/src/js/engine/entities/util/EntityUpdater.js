import MID from '../../../io/messages/MID.js';
import LOG from '../../../util/Log';
import entityCacheEx from '../EntityCacheEx';

/**
 * The updater will hook into the messaging system and listen for entity update
 * messages. If such a message is received it is responsible for updating and
 * translating this change message to commands for the bestia engine. If updates
 * to entities are detected this will be communicated 'upstream' via the use of
 * callback function. The engine/state can hook into the updater via them.
 * <p>
 * The updates of the entities are hold back until releaseHold is called.
 * </p>
 * 
 * @param {Bestia.PubSub}
 *            pubsub - Reference to the bestia publish/subscriber system for
 *            hooking into update calls.
 */
export default class EntityUpdater {

	constructor(ctx) {

		// === SUBSCRIBE ===
		ctx.pubsub.subscribe(MID.ENTITY_UPDATE, this._handlerOnUpdate.bind(this));
		ctx.pubsub.subscribe(MID.ENTITY_MOVE, this._handlerOnMove.bind(this));
		ctx.pubsub.subscribe(MID.ENTITY_POSITION, this._handlerOnPosition.bind(this));
	}

	/**
	 * Makes a complete update of the entity. Which can be a vanish, create or
	 * simple update.
	 */
	_handlerOnUpdate(_, msg) {
		switch (msg.a) {
			case 'UPDATE':
			case 'APPEAR':

				var entityData = {
					eid: msg.eid,
					sprite: { name: msg.s.s, type: msg.s.t },
					position: { x: msg.x, y: msg.y },
					action: 'appear'
				}

				entityCacheEx.addEntity(entityData);
				break;
			case 'VANISH':
			case 'DIE':
				var entity = entityCacheEx.getEntity(msg.eid);
				entity.action = 'remove';
				break;
		}
	}

	/**
	 * A movement prediction update was send. Plan the animation path to predict the
	 * movement of the entity.
	 */
	_handlerOnMove(_, msg) {
		if (this._isBuffered(msg)) {
			return;
		}

		var entity = entityCacheEx.getEntity(msg.eid);

		// Entity not in cache. We cant do anything.
		if (entity === null) {
			LOG.warn('Entity not found in cache. Can not move it.' + msg);
			return;
		}

		// Transform movement path of message.
		var path = [];
		for (var i = 0; i < msg.pX.length; i++) {
			var point = { x: msg.pX[i], y: msg.pY[i] };
			path.push(point);
		}

		// Attach the movement data to the entity.
		entity.movement = {
			path: path,
			speed: msg.w,
			delta: msg.d + msg.l
		};
	}

	/**
	 * A position update was send. Check if the entity is on this place or at least
	 * near it. If distance is too far away hard correct it.
	 */
	_handlerOnPosition(_, msg) {
		if (this._isBuffered(msg)) {
			return;
		}

		var entity = this._ctx.entityCache.getEntity(msg.eid);
		// Entity not in cache. We cant do anything.
		if (entity !== null) {
			//entity.setPosition(msg.x, msg.y);
		}

		// Update the alternate cache.
		entity = entityCacheEx.getEntity(msg.eid);
		if(entity !== null) {
			entity.position.x = msg.x;
			entity.position.y = msg.y;
		}
	}
}