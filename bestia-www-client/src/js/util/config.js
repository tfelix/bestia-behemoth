/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * Holds the Bestia configuration delivered by the server.info message.
 * Instances of this class will hook into the system and update their data upon
 * arrival of such a message.
 * 
 * @constructor
 * @class Bestia.Config
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber reference.
 */
Bestia.Config = function(pubsub) {

	var self = this;
	this.zones = ko.observableArray();
	this.version = ko.observable(0);
	this.server = ko.observable('');
	this.connectedPlayer = ko.observable(0);
	this.resourceURL = ko.observable('');

	this.userName = ko.observable('');

	/**
	 * ID of the authenticated account.
	 * 
	 * @public
	 * @property {Number} accountId
	 */
	this.accountId = ko.observable(0);

	/**
	 * @property {boolean} debug - Flag if we should enable debug information.
	 *           Later this should be splitted in hard debug only for
	 *           development and soft debug for all players.
	 */
	this.debug = ko.observable(true);

	/**
	 * Used locale of the user.
	 * 
	 * @property {string} locale
	 */
	this.locale = ko.observable('de-DE');

	/**
	 * Handler for setting the config values emitted by the server.
	 */
	var onMessageHandler = function(_, msg) {
		self.zones(msg.z);
		self.version(msg.v);
		self.connectedPlayer(msg.cp);
		self.resourceURL(msg.res);
		self.server(msg.sn);
	};

	/**
	 * Handler for setting the auth values emit by the system during login.
	 */
	var onAuthHandler = function(_, msg) {
		self.userName(msg.username);
		self.accountId(msg.accId);
	};

	// Register for messages.
	pubsub.subscribe('server.info', onMessageHandler);
	pubsub.subscribe('system.auth', onAuthHandler);
};