/*global Phaser */

import PubSub from '../util/PubSub';
import Signal from '../io/Signal';
import GetRefMessage from '../message/internal/GetRefMessage';


export default class EnginePubSub extends PubSub {
	

	constructor() {
		super();
	}
	
	/**
	 * Does an async callback request to the cache system to retrieve the
	 * reference to the requested object.
	 */
	getRef(name, callback) {
		this.publish(Signal.ENGINE_GETREF, {name: name, callback: callback});
	}
}