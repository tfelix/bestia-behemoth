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
	 * Removes the function from the publisher list.
	 */
	PubSub.prototype.unsubscribe = function(e, fn) {
		if (!this.cache[e]) {
			return;
		}
		var fns = this.cache[e];
		if (!fn) {
			fns.length = 0;
		}
		var index = fns.indexOf(fn);
		if (index !== 0) {
			fns.splice(index, 1);
		}
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

	Bestia.PubSub = new PubSub();
})(Bestia);