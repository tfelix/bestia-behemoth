/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from './Signal.js';
import Storage from '../util/Storage.js';
import Urls from '../Urls.js';

/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @class Bestia.Connection
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
export default class Connection {
	
	/**
	 * Ctor.
	 * 
	 * @constructor
	 */
	constructor(pubsub) {

		this._socket = null;

		/**
		 * Pubsub interface.
		 * 
		 * @property {Bestia.PubSub}
		 * @private
		 */
		this._pubsub = pubsub;

		// @ifdef DEVELOPMENT
		this.debugBytesSend = ko.observable(0);
		this.debugBytesReceived = ko.observable(0);
		this._debugData = {};
		// @endif

		// Sends a message while listening to this channel.
		pubsub.subscribe(Signal.IO_SEND_MESSAGE, this._handleOnSendMessage.bind(this));
		// wire connect.
		pubsub.subscribe(Signal.IO_CONNECT, this.connect.bind(this));
		// wire disconnect.
		pubsub.subscribe(Signal.IO_DISCONNECT, this.disconnect.bind(this));
	}
	
	_handleOnSendMessage(_, msg) {
		var message = JSON.stringify(msg);
		// @ifdef DEVELOPMENT
		console.trace('Sending Message: ' + message);
		this._debug('send', message, msg);
		// @endif
		if(this._socket !== null) {
			this._socket.send(message);
		}
	}
	
	/**
	 * Creates the debug statistics. Should be called when receiving or sending
	 * messages. Since this is a quite consuming process it should only be done
	 * in a developing state.
	 * 
	 * @private
	 * @param {String}
	 *            direction - ['send' | 'receive'] direction of the message.
	 * @param {String}
	 *            msgString - Stringified JSON of the message.
	 * @param {Object}
	 *            msgObj - Message object.
	 * 
	 */
	_debug(direction, msgString, msgObj) {
		if (direction === 'send') {
			this.debugBytesSend += msgString.length;
		} else {
			this.debugBytesReceived += msgString.length;
		}

		// Check if debug data struct exists. Or create it.
		if (!this._debugData.hasOwnProperty(msgObj.mid)) {
			this._debugData[msgObj.mid] = {
				count : 0,
				last : 0,
				bytes : 0
			};
		}

		this._debugData[msgObj.mid].count++;
		this._debugData[msgObj.mid].last = (new Date()).getTime();
		this._debugData[msgObj.mid].bytes += msgString.length;
	}

	/**
	 * Checks if logindata is ok or otherwise not complete. Returns true if
	 * everything is looking good. Otherwise false.
	 * 
	 * @private
	 * @param {Object}
	 *            data - The data object containting the login data.
	 * @method Bestia.Connection#checkLoginData
	 * @returns TRUE if all the login data is existing. FALSE if there is
	 *          something missing or an error.
	 */
	checkLoginData(data) {

		var state = true;

		if (!data) {
			console.error("No login data present.");
			state = false;
		}

		if (!state || data.token === undefined) {
			console.error("Login: token missing.");
			state = false;
		} else if (!state | data.accId === undefined) {
			console.error("Login: account id missing.");
			state = false;
		} else if (!state | data.username === undefined) {
			console.error("Login: username missing.");
			state = false;
		}

		if (state === false) {
			this._pubsub.publish(Signal.AUTH_ERROR);
			return false;
		}

		return true;
	}

	connect() {
		// defined a connection to a new socket endpoint
		var self = this;
		this._socket = new SockJS('http://localhost:8080/socket');
		this._socket.onopen = function() {
			// Prepare login message and send it.
			var loginMsg = {
				accId : 1,
				token : '04473c9f-65e9-4f59-9075-6da257a21826'
			};
			self._socket.send(JSON.stringify(loginMsg));
		};

		this._socket.onmessage = function(e) {
			// Is it a valid server message?
			try {
				var json = jQuery.parseJSON(e);

				// @ifdef DEVELOPMENT
				console.trace('Received Message: ' + e);
				self._debug('receive', json, e);
				// @endif

				self._pubsub.publish(json.mid, json);
			} catch (e) {
				console.error('No valid JSON: ', e);
				return;
			}
		};

		this._socket.onclose = function() {
			console.log('Server has closed the connection.');
			// Most likly we are not authenticated. Back to login.
			self._pubsub.publish(Signal.IO_DISCONNECTED);
		};
		
		this._pubsub.publish(Signal.IO_CONNECTING);
	}

	/**
	 * Disconnects the socket from the server.
	 */
	disconnect() {
		if (this._socket === null) {
			return;
		}
		this._socket.close();
		this._socket = null;
	}


	sendPing() {
		this._socket.push(JSON.stringify({
			mid : 'system.ping',
			m : 'Hello Bestia.'
		}));
	}
}

