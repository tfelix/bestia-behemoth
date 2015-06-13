/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

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
	self.server = ko.observable('');
	self.connectedPlayer = ko.observable(0);
	self.resourceURL = ko.observable('');
	/**
	 * @property {boolean} debug - Flag if we should enable debug information.
	 *           Later this should be splitted in hard debug only for
	 *           development and soft debug for all players.
	 */
	self.debug = ko.observable(true);
	self.locale = ko.observable('de-DE');

	var onMessageHandler = function(_, msg) {
		console.log('New configuration message arrived! Setting values.');

		self.zones(msg.z);
		self.version(msg.v);
		self.connectedPlayer(msg.cp);
		self.resourceURL(msg.res);
		self.server(msg.sn);
	};

	// Register for messages.
	Bestia.subscribe('server.info', onMessageHandler);
};