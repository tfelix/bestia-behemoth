/**
 * @author Thomas Felix
 * @copyright 2015 Thomas Felix
 */

import Signal from '../io/Signal';
import LOG from '../util/Log';

/**
 * Publish/Subscriber object. Central object for the game inter communucation.
 * 
 * @export
 * @class PubSub
 */
export default class PubSub {

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
		this._currentlyActive = false;

		/**
		 * @private
		 */
		this._publishCache = [];
	}

	/**
	 * Subscibes to the bestia publish subscriber model.
	 * 
	 * @param {string} e - Eventname or topic to subscribe to.
	 * @param {function} fn - Callback function which will get invoked if such an event
	 *            happens.
	 * @param {*} ctx - Context which is bound to the function.
	 */
	subscribe(e, fn, ctx) {
		if (typeof e !== 'string') {
			throw 'Eventname must be of type string.';
		}

		if (!this._cache[e]) {
			this._cache[e] = [];
		}

		if (ctx) {
			fn = fn.bind(ctx);
		}

		this._cache[e].push(fn);
	}

	/**
	 * Removes the function from the publisher list. If no function is given it
	 * removes ALL event handler from the given callback name.
	 * 
	 * @param {string} e - Name of the event handler.
	 * @param {function} fn - Function to be removed from this handler.
	 * @returns {boolean} TRUE upon success, FALSE otherwise.
	 */
	unsubscribe(e, fn) {

		// Hold the calls until we are finished iterating.
		if (this._currentlyActive === true) {
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
		if (this._currentlyActive) {
			return;
		}

		this._holdUnsubscribeCalls.forEach(function (fn) {
			fn();
		}, this);
		this._holdUnsubscribeCalls = [];
	}

	/**
	 * This directly sends a message request to the server. It is shortcut for
	 * publish(IO_SEND_MESSAGE, data);
	 *
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

		if (!e) {
			throw 'Topic can not be undefined.';
		}

		if (!this._cache[e]) {
			return;
		}

		this._publishCache.push({ topic: e, data: data });
		this._performPublish();
	}

	_performPublish() {

		if (this._currentlyActive === true) {
			return;
		} else {
			this._currentlyActive = true;
		}

		while (this._publishCache.length > 0) {
			let d = this._publishCache.shift();
			let fns = this._cache[d.topic];
			let fnsCount = fns.length;

			// @ifdef DEVELOPMENT
			LOG.debug('Published:', d.topic, '- Data:', d.data);
			// @endif

			for (var i = 0; i < fnsCount; ++i) {
				try {

					// @ifdef DEVELOPMENT
					//LOG.trace('Calling fn:', fns[i].name);
					// @endif

					fns[i](d.topic, d.data);
				} catch (ex) {
					LOG.error('Error while publish topic:', d.topic, 'Function:', fns[i].name, 'Error:', ex);
				}
			}
		}
		this._currentlyActive = false;
		this._updateCache();
	}
}

