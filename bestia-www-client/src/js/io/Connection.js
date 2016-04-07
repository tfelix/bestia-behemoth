/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from 'io/Signal.js';

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
		var self = this;

		this.socket = null;

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
		pubsub.subscribe(Signal.IO_SEND_MESSAGE, function(_, msg) {
			var message = JSON.stringify(msg);
			// @ifdef DEVELOPMENT
			console.trace('Sending Message: ' + message);
			self._debug('send', message, msg);
			// @endif
			self.socket.push(message);
		});
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
		var socketRequest = this._init();
		this._pubsub.publish(Signal.IO_CONNECTING);
		this.socket = $.atmosphere.subscribe(socketRequest);
	}

	/**
	 * Disconnects the socket from the server.
	 */
	disconnect() {
		if (this.socket === null) {
			return;
		}
		this.socket.close();
		this.socket = null;
	}

	/**
	 * - Initializes a connection to the server using the login data present in
	 * a cookie which must be acquired via the login process before trying to
	 * establish this connection.
	 * 
	 * Publishes: system.auth - Containing the auth data (bestia name, user id)
	 * if a successful connection to the server has been established.
	 * 
	 * @method Bestia.Connection#_init
	 */
	_init() {

		var self = this;

		// Prepare the request.
		var store = new Bestia.Storage();
		var authData = store.getAuth();

		if (!this.checkLoginData(authData)) {
			return;
		}

		// Emit the auth data signal so other parts of the app can react to it.
		this._pubsub.publish(Bestia.Signal.AUTH, authData);

		var request = {
			url : Bestia.Urls.bestiaWebsocket,
			contentType : "application/json",
			logLevel : 'info',
			transport : 'websocket',
			headers : {
				'X-Bestia-Token' : authData.token,
				'X-Bestia-Account' : authData.accId
			},
			maxReconnectOnClose : 5,
			trackMessageLength : true,
			enableProtocol : true
		};

		request.onOpen = function(response) {
			console.log('Connection to established via ' + response.transport);
			self._pubsub.publish(Signal.IO_CONNECTED, {});
		};

		request.onTransportFailure = function(errorMsg) {
			console.log('Error while failing transport: ' + errorMsg);
			jQuery.atmosphere.info(errorMsg);
		};

		/**
		 * Handle an incoming message from the server. The message is filtered,
		 * parsed to JSON and then delivered to the pub-sub mechanism.
		 */
		request.onMessage = function(response) {
			var message = response.responseBody;

			// Ignore keepalive.
			if (message == 'X') {
				return;
			}

			// Is it a valid server message?
			try {
				var json = jQuery.parseJSON(message);

				// @ifdef DEVELOPMENT
				console.trace('Received Message: ' + message);
				self._debug('receive', json, message);
				// @endif

				self._pubsub.publish(json.mid, json);

			} catch (e) {
				console.error('No valid JSON: ', e);
				return;
			}
		};

		/**
		 * Handle the close event.
		 */
		request.onClose = function() {
			console.log('Server has closed the connection.');
			// Most likly we are not authenticated. Back to login.
			self._pubsub.publish(Signal.IO_DISCONNECTED);
		};

		/**
		 * Handle the error event.
		 */
		request.onError = function() {
			console.error('Server error. Can not create connection.');
			// Most likly we are not authenticated. Back to login.
			self._pubsub.publish(Signal.IO_DISCONNECTED);
		};
		
		return request;
	}

	sendPing() {
		this.socket.push(JSON.stringify({
			mid : 'system.ping',
			m : 'Hello Bestia.'
		}));
	}
	
}

