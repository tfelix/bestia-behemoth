/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

(function(Bestia, ko) {
	'use strict';
	
	/**
	 * Holds the Bestia configuration delivered by the server.info message.
	 * Instances of this class will hook into the system and update their data upon
	 * arrival of such a message.
	 * 
	 * @class Bestia.Config
	 */
	Bestia.Config = function() {

		var self = this;
		self.zones = ko.observableArray();
		self.version = ko.observable(0);
		self.server = ko.observable();
		self.connectedPlayer = ko.observable(0);
		self.resourceURL = ko.observable('');
		self.debug = ko.observable(false);
		self.locale = ko.observable('de-DE');

		var onMessageHandler = function(_, msg) {
			console.log('New configuration message arrived! Setting values.');

			self.zones(msg.z);
			self.version(msg.v);
			self.connectedPlayer(msg.cp);
			self.resourceURL(msg.res);
			self.server(msg.zn);
		};

		// Register for messages.
		$.subscribe('server.info', onMessageHandler);
	};
})(Bestia, ko);