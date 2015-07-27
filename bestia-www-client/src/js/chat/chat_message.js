/**
 * View Model for single messages.
 * 
 * @class Bestia.ChatMessage
 * @param {Object}
 *            msg - Message object to create the model.
 */
Bestia.ChatMessage = function(msg) {
	var self = this;

	/**
	 * Nickname of the user.
	 * 
	 * @public
	 * @property {string}
	 */
	self.nickname = ko.observable(msg.sn);

	/**
	 * Activated mode of the chat.
	 * 
	 * @public
	 * @property {string}
	 */
	self.mode = ko.observable(msg.m);

	/**
	 * Text of the chat message.
	 * 
	 * @public
	 * @property {string}
	 */
	self.text = ko.observable(msg.txt);

	/**
	 * Holds the CSS style name of the currently activated chatmode.
	 * 
	 * @public
	 * @property {string}
	 */
	self.cssMode = ko.pureComputed(function() {
		return self.mode().toLowerCase();
	});
};