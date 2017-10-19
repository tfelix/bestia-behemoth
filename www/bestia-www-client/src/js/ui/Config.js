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
 * @param {Pubsub}
 *            pubsub - Publish/Subscriber reference.
 */
export default class Config {
	
	constructor(pubsub) {

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
		
		// Engine
		this.engine = {
			enableMusic : ko.observable(true),
			volumeMusic : ko.observable(1.0),
			enableSoundFx : ko.observable(true),
			volumeSoundFx : ko.observable(1.0),
			
			debug : ko.observable(true)
		};
	
		// Register for messages.
		pubsub.subscribe('server.info', this._handleOnMessage.bind(this));
		pubsub.subscribe('system.auth', this._handleOnAuth.bind(this));
	}
	
	/**
	 * Handler for setting the config values emitted by the server.
	 */
	_handleOnMessage(_, msg) {
		this.zones(msg.z);
		this.version(msg.v);
		this.connectedPlayer(msg.cp);
		this.resourceURL(msg.res);
		this.server(msg.sn);
	}
	
	/**
	 * Handler for setting the auth values emit by the system during login.
	 */
	_handleOnAuth(_, msg) {
		this.userName(msg.username);
		this.accountId(msg.accId);
	}
}
