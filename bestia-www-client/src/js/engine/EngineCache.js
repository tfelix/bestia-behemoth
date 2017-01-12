/*global Phaser */

import PubSub from '../util/PubSub';
import Signal from '../io/Signal';

/**
 * This is a special variant of the pub sub implementation. It will keep
 * references to the registered components and with a special message they can
 * be retrieved. This is very useful to decouple the party of the engine from
 * each other.
 */
export default class EngineCache {
	constructor(pubsub) {
		
		this._pubsub = pubsub;
		
		this._components = [];
		
		// ### Subscribe callbacks
		this._pubsub.subscribe(Signal.ENGINE_GETREF, this._returnRef, this);
		this._pubsub.subscribe(Signal.ENGINE_SETREF, this._setRef, this);
	}
	
	_returnRef(msg) {
		
	}
	
	_setRef(component) {
		
	}
	
	/**
	 * Directly registering a component without using the signal ENGINE_SETREF.
	 */
	registerComponent(component) {
		this._setRef(component);
	}
}