import { guid } from '../util/Guid';
import Message from '../io/messages/Message';
import MID from '../io/messages/MID';
import Signal from '../io/Signal';
import LOG from '../util/Log';

/**
 * Registers to the pubsub and listens to client var request calls. 
 * This will send out a message to the server and awaits the response.
 */
export default class ClientVarManager {

    /**
     * Ctor. Awaits a pubsub object.
     */
	constructor(pubsub) {

		if (!pubsub) {
			throw 'Pubsub can not be null or empty.';
		}

		this._callbackCache = {};
		this._pubsub = pubsub;

		pubsub.subscribe(MID.CVAR, this._handleCvarResponse, this);
		pubsub.subscribe(Signal.CVAR, this._handleCvarResponse, this);
	}

    /**
     * Handles the request from the client side. Creates a request message and sends it to the server. Handles also
     * the async callback invocation.
     * @param {String} _ Topic name
     * @param {Object} data Data containig details about the request. 
     */
	_handleCvarRequest(_, data) {
		// Check if we have a callback given.
		if (!data.fn) {
			throw 'Property fn as callback is missing.';
		}

		if (!data.key) {
			throw 'Property key was not given.';
		}

		var uid = guid();

		this._callbackCache[uid] = { success: data.fn, error: data.fnErr };

		// If the request is not answered after a given time an error will be signaled.
		window.setTimeout(function () {
			this._checkError(uid);
		}.bind(this), 10000);

		var msg = null;

		if (data.mode.toUpperCase() === 'SET') {
			msg = Message.CvarRequest(uid, data.key, data.mode, data.data);
		} else {
			msg = Message.CvarRequest(uid, data.key, data.mode);
		}

		this._pubsub.send(msg);
	}

    /**
     * When the server responds we will check if there 
     * is a waiting callback for the request and fire it.
     * @param {*} _ 
     * @param {*} data 
     */
	_handleCvarResponse(_, data) {
		if (!this._callbackCache.hasOwnProperty(data.uid)) {
			return;
		}

		try {
			this._callbackCache[data.uid].error(data.uid);
		} catch (e) {
			LOG.error(e);
		}

		delete this._callbackCache[data.uid];
	}

    /**
     * Checks if the key was already resolved. If not then it will signal an error.
     * @param {string} uid 
     */
	_checkError(uid) {
		if (!this._callbackCache.hasOwnProperty(uid)) {
			// Everything okay.
			return;
		}

		if (this._callbackCache[uid].error) {
			try {
				this._callbackCache[uid].error(uid);
			} catch (e) {
				LOG.error(e);
			}
		}

		delete this._callbackCache[uid];
	}

}