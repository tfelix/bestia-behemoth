import MID from '../../../io/messages/MID.js';
import LOG from '../../../util/Log';
import entityCacheEx from '../EntityCacheEx';
import {phaserSpriteCache, phaserPlayerSprite} from './../../PhaserSpriteCache';

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
		if (!ctx) {
			throw 'Context can not be undefined.';
		}

		/**
		 * Temporary buffer to hold all the data until releaseHold is called.
		 */
		this._buffer = [];

		this._ctx = ctx;

		// === SUBSCRIBE ===
		this._ctx.pubsub.subscribe(MID.ENTITY_UPDATE, this._handlerOnUpdate.bind(this));
		this._ctx.pubsub.subscribe(MID.ENTITY_MOVE, this._handlerOnMove.bind(this));
		this._ctx.pubsub.subscribe(MID.ENTITY_POSITION, this._handlerOnPosition.bind(this));
	}

	/**
	 * Makes a complete update of the entity. Which can be a vanish, create or
	 * simple update.
	 */
	_handlerOnUpdate(_, msg) {
		if (this._isBuffered(msg)) {
			return;
		}

		switch (msg.a) {
			case 'UPDATE':
			case 'APPEAR':
				var entity = this._ctx.entityCache.getEntity(msg.eid);

				if (entity !== null) {
					// Exists already. Strange.
					return;
				}

				var entityData = {
					eid: msg.eid,
					sprite: { name: msg.s.s, type: msg.s.t },
					position: { x: msg.x, y: msg.y }
				}

				entityCacheEx.addEntity(entityData);

				// Build the display object and attach it to the sprite cache.
				this._ctx.entityFactory.build(msg, function(displayObj){
					phaserSpriteCache[msg.eid] = displayObj;

					if(msg.eid === this._ctx.playerBestia.entityId()) {
						phaserPlayerSprite = displayObj;
					}
				});
				break;
			case 'VANISH':
			case 'DIE':
				var entity = this._ctx.entityCache.getEntity(msg.eid);
				entity.remove();
				this._ctx.entityCache.removeEntity(entity);
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
			detla: msg.d + msg.l
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

	/**
	 * Checks if the buffering is still active. If this is the case buffer the
	 * message and return true else return false.
	 * 
	 * @param msg
	 * @returns {Boolean}
	 */
	_isBuffered(msg) {
		if (this._buffer !== undefined) {
			this._buffer.push({
				topic: msg.mid,
				msg: msg
			});
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Holds the entity updates in a cache until the engine has loaded the map. When
	 * this method is called all updates are forwarded to the engine.
	 */
	releaseHold() {
		if (this._buffer === undefined) {
			return;
		}

		var temp = this._buffer;
		this._buffer = undefined;
		temp.forEach(function (d) {
			switch (d.topic) {
				case MID.ENTITY_UPDATE:
					this._handlerOnUpdate(d.topic, d.msg);
					break;
				case MID.ENTITY_MOVE:
					this._handlerOnMove(d.topic, d.msg);
					break;
				case MID.ENTITY_POSITION:
					this._handlerOnPosition(d.topic, d.msg);
					break;
				default:
					break;
			}
		}, this);
	}
}