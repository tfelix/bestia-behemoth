/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */
(function(Bestia) {
	'use strict';

	function PubSub() {
		this.cache = {};
	}

	/**
	 * Subscibes to the bestia publish subscriber model.
	 * 
	 * @method Bestia#subscribe
	 * @param
	 */
	PubSub.prototype.subscribe = function(e, fn) {
		if (!this.cache[e]) {
			this.cache[e] = [];
		}
		this.cache[e].push(fn);
	};

	/**
	 * Removes the function from the publisher list. If no function is given it
	 * removes ALL event handler from the given callback name.
	 * 
	 * @method Bestia.PubSub#unsubscribe
	 * @param {String}
	 *            e - Name of the event handler.
	 * @param {Function}
	 *            fn - Function to be removed from this handler.
	 * @returns {Boolean} TRUE upon success, FALSE otherwise.
	 */
	PubSub.prototype.unsubscribe = function(e, fn) {
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
	};

	/**
	 * Publishes a message to the subscribed listener.
	 * 
	 * @param {String}
	 *            e - Name
	 */
	PubSub.prototype.publish = function(e, data) {
		if (!this.cache[e]) {
			return;
		}
		var fns = this.cache[e];
		for (var i = 0; i < fns.length; ++i) {
			fns[i](e, data);
		}
	};

	Bestia.Util = {
		PubSub : PubSub
	};
	
	var pubsub = new PubSub();
	Bestia.subscribe = pubsub.subscribe.bind(pubsub);
	Bestia.unsubscribe = pubsub.unsubscribe.bind(pubsub);
	Bestia.publish = pubsub.publish.bind(pubsub);
})(Bestia);