/*global Phaser */

import PubSub from '../util/PubSub';
import Signal from '../io/Signal';
import GetRefMessage from '../message/internal/GetRefMessage';

/**
 * This is a special variant of the pub sub implementation. It will keep
 * references to the registered components and with a special message they can
 * be retrieved. This is very useful to decouple the party of the engine from
 * each other.
 */
export default class EngineCache {
	
	/**
	 * Ctor.
	 * 
	 * @param {PubSub}
	 *            pubsub - PublishSubscriber object.
	 */
	constructor(pubsub) {
		
		this._pubsub = pubsub;
		
		this._components = [];
		
		/**
		 * If a component is requested but not yet registered the call will be
		 * cached until this happend and then it will resolve.
		 */
		this._callbackCache = {};
		
		// ### Subscribe callbacks
		this._pubsub.subscribe(Signal.ENGINE_GETREF, this._returnRef, this);
		this._pubsub.subscribe(Signal.ENGINE_SETREF, this._setRef, this);
	}
	
	_returnRef(_, msg) {
		if(!(msg instanceof GetRefMessage)) {
			throw 'Message was no instance of type GetRefMessage.';
		}
	
		for(let i = 0; i < this._components.length; i++) {
			if(this._components[i].NAME === msg.name) {
				msg.callback(this._components[i]);
				return;
			}
		}
		
		// Cache the callback.
		this._callbackCache[msg.name] = [msg.callback];
	}
	
	_setRef(_, component) {
		if(!component || !component.NAME) {
			throw 'Component can not be null and must have a NAME property.';
		}
		
		this._components.push(component);
		
		// Check if there are components waiting for this one.
		if(this._callbackCache.hasOwnProperty(component.NAME)) {
			this._callbackCache[component.NAME].forEach(callback => {
				callback(component);
			});
			delete this._callbackCache[component.NAME];
		}
	}
	
	/**
	 * Directly registering a component without using the signal ENGINE_SETREF.
	 */
	registerComponent(component) {
		this._setRef(null, component);
	}
}