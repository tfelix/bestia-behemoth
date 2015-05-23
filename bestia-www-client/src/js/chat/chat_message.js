/**
 * View Model for single messages.
 * 
 * @class Bestia.ChatMessage
 * @param {Object} msg - Message object to create the model.
 */
Bestia.ChatMessage = function(msg) {
	var self = this;

	self.nickname = ko.observable(msg.sn);
	self.mode = ko.observable(msg.m);
	self.text = ko.observable(msg.txt);

	self.cssMode = ko.pureComputed(function() {
		return self.mode().toLowerCase();
	});
};