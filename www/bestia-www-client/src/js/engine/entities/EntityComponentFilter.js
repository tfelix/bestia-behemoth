import MID from '../../io/messages/MID';

/**
 * 
 * 
 * @export
 * @class EntityComponentFilter
 */
export default class EntityComponentFilter {
	constructor(pubsub) {

		this._callbacks = {};

		pubsub.subscribe(MID.ENTITY_COMPONENT_UPDATE, this._onComponentUpdate, this);
		//pubsub.subscribe(MID.ENTITY_COMPONENT_DELETE, this._onComponentDelete, this);
	}

	/**
	 * 
	 * 
	 * @param {any} componentType Identifier for the component when incoming component will trigger the callback.
	 * @param {function} fn Function callback to trigger when the component changes.
	 * @memberof EntityComponentFilter
	 */
	addCallbackUpdate(componentType, fn) {
		if (!this._callbacks.hasOwnProperty(componentType)) {
			this._callbacks[componentType] = [];
		}

		this._callbacks[componentType].push(fn);
	}

	_onComponentUpdate(_, msg) {
		if (this._callbacks.hasOwnProperty(msg.ct)) {
			this._callbacks[msg.ct].forEach(function(callback) {
				callback(msg);
			});
		}
	}
}
