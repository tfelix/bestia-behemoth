/*global SockJS */

/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from './Signal.js';
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
		pubsub.subscribe(Signal.IO_SEND_MESSAGE, this._handleOnSendMessage, this);
		// wire connect.
		pubsub.subscribe(Signal.IO_CONNECT, this.connect, this);
		// wire disconnect.
		pubsub.subscribe(Signal.IO_DISCONNECT, this.disconnect, this);
	}
	
	_handleOnSendMessage(_, msg) {
		var message = JSON.stringify(msg);
		
		// @ifdef DEVELOPMENT
		console.debug('Sending Message: ' + message);
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

	connect() {
		// defined a connection to a new socket endpoint
		this._socket = new SockJS(Urls.bestiaWebsocket);
		this._socket.onopen = function() {
			this._pubsub.publish(Signal.IO_CONNECTED);
		}.bind(this);

		this._socket.onmessage = function(e) {
			// Is it a valid server message?
			try {
				var json = jQuery.parseJSON(e.data);
				// @ifdef DEVELOPMENT
				console.debug('Received Message: ' + e.data);
				this._debug('receive', json, e.data);
				// @endif

				this._pubsub.publish(json.mid, json);
			} catch (ex) {
				console.error('No valid JSON: ', e);
				return;
			}
		}.bind(this);

		this._socket.onclose = function() {
			console.log('Server has closed the connection.');
			// Most likly we are not authenticated. Back to login.
			this._pubsub.publish(Signal.IO_DISCONNECTED);
		}.bind(this);
		
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

	/**
	 * Sends ping to the server.
	 */
	sendPing() {
		this._socket.send(JSON.stringify({
			mid : 'system.ping',
			m : 'Hello Bestia.'
		}));
	}
}

