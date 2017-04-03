/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 * @module util/PubSub
 */

import Signal from '../io/Signal';
import LOG from '../util/Log';

/**
 * Publish/Subscriber object. Central object for the game inter communucation.
 * 
 * @class PubSub
 */
export default class PubSub {
	
	/**
	 * Creates a PubSub object.
	 * 
	 * @constructs PubSub
	 */
	constructor() {

		/**
		 * @private
		 */
		this._cache = {};

		/**
		 * @private
		 */
		this._holdUnsubscribeCalls = [];

		/**
		 * @private
		 */
		this._currentlyActive = 0;
	}
	
	/**
	 * Subscibes to the bestia publish subscriber model.
	 * 
	 * @member PubSub#subscribe
	 * @param {string}
	 *            e - Eventname or topic to subscribe to.
	 * @param {function}
	 *            fn - Callback function which will get invoked if such an event
	 *            happens.
	 * @param {*}
	 *            [ctx] - Context which is bound to the function.
	 */
	subscribe(e, fn, ctx) {
		if(typeof e !== 'string') {
			throw 'Eventname must be of type string.';
		}
		
		if (!this._cache[e]) {
			this._cache[e] = [];
		}
		
		if(ctx) {
			fn = fn.bind(ctx);
		}
		
		this._cache[e].push(fn);
	}

	/**
	 * Removes the function from the publisher list. If no function is given it
	 * removes ALL event handler from the given callback name.
	 * 
	 * @method PubSub#unsubscribe
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

		if (!this._cache[e]) {
			return false;
		}
		var fns = this._cache[e];
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
	 * 
	 * @private
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
	 * 
	 * @method PubSub#send
	 * @public
	 * @param {Object} data - Data to be send to the server.
	 */
	send(data) {
		this.publish(Signal.IO_SEND_MESSAGE, data);
	}

	/**
	 * Publishes a message to the subscribed listener.
	 * 
	 * @method PubSub#publish
	 * @public
	 * @param {string} e - The topic name under which the data is published.
	 * @param {*} [data] - Data which is published to this topic.
	 */
	publish(e, data) {
		
		if(!e) {
			throw 'Topic can not be undefined.';
		}

		// @ifdef DEVELOPMENT
		LOG.debug('Published:', e, '- Data:', data);
		// @endif

		if (!this._cache[e]) {
			return;
		}
		this._currentlyActive++;
		var fns = this._cache[e];
		var fnsCount = fns.length;
		for (var i = 0; i < fnsCount; ++i) {
			try {
				fns[i](e, data);
			} catch(ex) {
				LOG.error('Error while publish topic: ', e, ex);
			}
		}
		this._currentlyActive--;
		this._updateCache();
	}
}

