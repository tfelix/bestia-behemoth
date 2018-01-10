/**
 * @author Thomas Felix
 * @copyright 2015 Thomas Felix
 */

import Signal from './Signal.js';
import MID from '../io/messages/MID';
import LatencyResponseMessage from '../message/external/LatencyResponseMessage';
import Urls from '../Urls.js';
import ko from 'knockout';
import SockJS from '../../../node_modules/sockjs-client/dist/sockjs';
import LOG from '../util/Log';

/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * The reconnection implements a backoff algorithm if a disconnection happens.
 * 
 * @class Connection
 */
export default class Connection {

	/**
	 * Ctor.
	 * 
	 * @constructor
	 */
	constructor(pubsub, shouldReconnect = false) {

		this._socket = null;

		/**
		 * Pubsub interface.
		 * 
		 * @property {Bestia.PubSub}
		 * @private
		 */
		this._pubsub = pubsub;

		/**
		 * Flag if a reconnection attempt should be made.
		 */
		this.shouldReconnect = shouldReconnect;
		this._connectionTries = 0;
		this._timeoutHndl = 0;

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
		if(msg.mid !== LatencyResponseMessage.MID) {
			LOG.debug('Sending Message: ' + message);
			this._debug('send', message, msg);
		}
		// @endif

		if (this._socket !== null) {
			this._socket.send(message);
		}
	}

	/**
	 * Creates the debug statistics. Should be called when receiving or sending
	 * messages. Since this is a quite consuming process it should only be done
	 * in a developing state.
	 * 
	 * @private
	 * @param {String} direction - ['send' | 'receive'] direction of the message.
	 * @param {String} msgString - Stringified JSON of the message.
	 * @param {Object} msgObj - Message object.
	 * 
	 */
	_debug(direction, msgString, msgObj) {
		if (direction === 'send') {
			this.debugBytesSend(this.debugBytesSend() + msgString.length);
		} else {
			this.debugBytesReceived(this.debugBytesReceived() + msgString.length);
		}

		// Check if debug data struct exists. Or create it.
		if (!this._debugData.hasOwnProperty(msgObj.mid)) {
			this._debugData[msgObj.mid] = {
				count: 0,
				last: 0,
				bytes: 0
			};
		}

		this._debugData[msgObj.mid].count++;
		this._debugData[msgObj.mid].last = (new Date()).getTime();
		this._debugData[msgObj.mid].bytes += msgString.length;
	}

	/**
	 * Tries to reconnect with a exponential backoff.
	 */
	_connectRetry() {
		this._connectionTries++;

		let time = this._connectionTries * this._connectionTries * 1500;
		if (time > 45000) {
			time = 45000;
		}
		LOG.debug('Retry connecting in', time / 1000, 's.');
		this._timeoutHndl = setTimeout(this.connect.bind(this), time);
	}

	connect() {
		LOG.info('Starting to connect.');

		// defined a connection to a new socket endpoint
		this._socket = new SockJS(Urls.bestiaWebsocket);

		this._socket.onopen = function () {
			LOG.debug('Connection opened.');
			this._connectionTries = 0;
			this._pubsub.publish(Signal.IO_CONNECTED);
		}.bind(this);

		this._socket.onmessage = this._handleMessage.bind(this);

		this._socket.onclose = function () {
			LOG.info('Server has closed the connection.');
			// Most likly we are not authenticated. Back to login.
			this._pubsub.publish(Signal.IO_DISCONNECTED);

			if(this.shouldReconnect) {
				this._connectRetry();
			}		
		}.bind(this);

		this._pubsub.publish(Signal.IO_CONNECTING);
	}

	_handleMessage(e) {
		// Is it a valid server message?
		let json;
		try {
			json = JSON.parse(e.data);
		} catch (ex) {
			LOG.error('No valid JSON: ', e);
			return;
		}

		// @ifdef DEVELOPMENT
		// Only show non latency messages.
		if(json.mid !== MID.LATENCY_REQ) {
			LOG.debug('Received Message: '+ e.data);
			this._debug('receive', json, e.data);
		}
		// @endif

		this._pubsub.publish(json.mid, json);
	}

	/**
	 * Disconnects the socket from the server.
	 */
	disconnect() {
		
		this.shouldReconnect = false;
		if (this._timeoutHndl !== 0) {
			clearTimeout(this._timeoutHndl);
		}
		this._connectionTries = 0;
		this._timeoutHndl = 0;
		
		if (this._socket !== null) {
			this._socket.close();
			this._socket = null;
		}		
	}
}

