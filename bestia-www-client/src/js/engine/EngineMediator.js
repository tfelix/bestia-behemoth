/*global Phaser */

import PubSub from '../util/PubSub';
import Signal from '../io/Signal';
import GetRefMessage from '../message/internal/GetRefMessage';

/**
 * Binds the engine pubsub and the outside world pubsub. Topics can be choosen
 * on a single basis to get forwarded to the engine. IO_SEND_MESSAGE topics are
 * forwarded to the engine.
 */
export default class EngineMediator {
	constructor(enginePubSub, pubsub) {
		
		this._pubsub = pubsub;
		this._enginePubSub = enginePubSub;
		
		// ### Subscribe callbacks
		this._enginePubSub.subscribe(Signal.IO_SEND_MESSAGE, this._forwardToWorld, this);
		this._enginePubSub.subscribe(Signal.IO_CONNECT, this._forwardToWorld, this);
	}
	
	/**
	 * Forwards the given topic to the world.
	 */
	forwardTopicToEngine(topic) {
		this._pubSub.subscribe(topic, this._forwardToEngine, this);
	}
	
	/**
	 * Forwards the message to the outside world.
	 */
	_forwardToWorld(topic, msg) {
		this._pubsub.publish(topic, msg);
	}
	
	/**
	 * Forwards the message to the engine subsystem.
	 */
	_forwardToEngine(topic, msg) {
		this._enginePubSub.publish(topic, msg);
	}
}