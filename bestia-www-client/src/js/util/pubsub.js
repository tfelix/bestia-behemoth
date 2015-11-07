/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Publish/Subscriber object. Central object for the game inter communucation.
 * 
 * @constructor
 * @class Bestia.PubSub
 */
Bestia.PubSub = function() {
	this.cache = {};
};

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
Bestia.PubSub.prototype.subscribe = function(e, fn) {
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
 * @param {string}
 *            e - Name of the event handler.
 * @param {function}
 *            fn - Function to be removed from this handler.
 * @returns {boolean} TRUE upon success, FALSE otherwise.
 */
Bestia.PubSub.prototype.unsubscribe = function(e, fn) {
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
Bestia.PubSub.prototype.publish = function(e, data) {
	if (!this.cache[e]) {
		return;
	}
	var fns = this.cache[e];
	for (var i = 0; i < fns.length; ++i) {
		fns[i](e, data);
	}
};

/**
 * Shortcut method. It will publish/send the message directly to the server.
 * Shortcut for publish('io.sendMessage', msg)
 * 
 * @param {Bestia.Message}
 *            msg - Message object to be send to the server.
 */
Bestia.PubSub.prototype.send = function(msg) {
	this.publish('io.sendMessage', msg);
};
