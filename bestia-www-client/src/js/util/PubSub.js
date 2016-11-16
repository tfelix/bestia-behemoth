/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from '../io/Signal';

/**
 * Publish/Subscriber object. Central object for the game inter communucation.
 * 
 * @constructor
 * @class PubSub
 */
export default class PubSub {
	
	constructor() {
		this.cache = {};
		this._holdUnsubscribeCalls = [];
		this._currentlyActive = 0;
	}
	
	/**
	 * Subscibes to the bestia publish subscriber model.
	 * 
	 * @method Bestia#subscribe
	 * @param {string}
	 *            e - Eventname or topic to subscribe to.
	 * @param {function}
	 *            fn - Callback function which will get invoked if such an event
	 *            happens.
	 */
	subscribe(e, fn) {
		if (!this.cache[e]) {
			this.cache[e] = [];
		}
		this.cache[e].push(fn);
	}

	/**
	 * Removes the function from the publisher list. If no function is given it
	 * removes ALL event handler from the given callback name.
	 * 
	 * @method Bestia.PubSub#unsubscribe
	 * @param {string}
	 *            e - Name of the event handler.
	 * @param {function}
	 *            fn - Function to be removed from this handler.
	 * @returns {boolean} TRUE upon success, FALSE otherwise.
	 */
	unsubscribe(e, fn) {

		// Hold the calls until we are finished iterating.
		if (this._currentlyActive > 0) {
			this._holdUnsubscribeCalls.push(this.unsubscribe.bind(this, e, fn));
			return;
		}

		if (!this.cache[e]) {
			return false;
		}
		var fns = this.cache[e];
		if (!fn) {
			// No function given. Remove all handler.
			fns.length = 0;
			return true;
		}
		var index = fns.indexOf(fn);
		if (index !== -1) {
			fns.splice(index, 1);
			return true;
		}

		return false;
	}

	/**
	 * Private function which will modify the cache. This must be done this way
	 * because we need a mechanism to wait for updating the cache when we are
	 * currently iterating over it.
	 */
	_updateCache() {
		// Guard. If there are still active iterations, avoid infinte loop.
		if (this._currentlyActive > 0) {
			return;
		}

		this._holdUnsubscribeCalls.forEach(function(fn) {
			fn();
		}, this);
		this._holdUnsubscribeCalls = [];
	}
	
	/**
	 * This directly sends a message request to the server. It is shortcut for
	 * publish(IO_SEND_MESSAGE, data);
	 */
	send(data) {
		this.publish(Signal.IO_SEND_MESSAGE, data);
	}

	/**
	 * Publishes a message to the subscribed listener.
	 * 
	 * @param {String}
	 *            e - Name
	 */
	publish(e, data) {
		
		if(!e) {
			throw "Topic can not be undefined.";
		}

		// @ifdef DEVELOPMENT
		console.debug('Published: ' + e + '.');
		if(data) {
			console.debug(data);
		}
		// @endif

		if (!this.cache[e]) {
			return;
		}
		this._currentlyActive++;
		var fns = this.cache[e];
		var fnsCount = fns.length;
		for (var i = 0; i < fnsCount; ++i) {
			try {
				fns[i](e, data);
			} catch(ex) {
				console.error('Error while publish topic: ' + e, ex);
			}
		}
		this._currentlyActive--;
		this._updateCache();
	}
}

