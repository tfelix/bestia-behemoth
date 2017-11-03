/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from './Signal';
import Urls from '../Urls';
import MID from './messages/MID';
import LOG from '../util/Log';
import AuthenticateMessage from '../message/external/AuthenticateMessage';

/**
 * Class which reads the login token from a storage system and tries to
 * authenticate with the server upon a connection.
 */
export default class Authenticator {
	
	/**
	 * 
	 * @param {Pubsub} pubsub 
	 * @param {Storage} storage 
	 */
	constructor(pubsub, storage) {

		if(!pubsub) {
			throw 'PubSub can not be null.';
		}

		if(!storage) {
			throw 'Storage can not be null.';
		}
		
		this._pubsub = pubsub;
		this._storage = storage;
		
		this._pubsub.subscribe(Signal.IO_CONNECTED, this._onConnected, this);
		this._pubsub.subscribe(MID.SYSTEM_AUTHREPLY, this._onAuthReply, this);
	}
	
	/**
	 * Sends auth message if the system is connected.
	 */
	_onConnected() {
		LOG.debug('Connection established. Starting to authenticate with server.');

		var authToken = this._storage.getAuth();

		var authMsg = new AuthenticateMessage(
			authToken.accountId, 
			authToken.token);

		this._pubsub.send(authMsg);
	}
	
	/**
	 * Reads the auth reply from the server.
	 */
	_onAuthReply(_, msg) {
		LOG.trace('Received auth reply.');

		if(msg.state === 'ACCEPTED') {
			LOG.debug('Login was accepted.');
			this._pubsub.publish(Signal.IO_AUTH_CONNECTED, msg);
		} else {
			LOG.debug('Login was denied.');
			this._pubsub.publish(Signal.IO_AUTH_ERROR);
			this._pubsub.publish(Signal.IO_DISCONNECT);
			// Go to login if there is wrong data.
			if(window && windows.location) {
				window.location.replace(Urls.loginHtml);
			}
		}
	}
	
	/**
	 * Checks if logindata is ok or otherwise not complete. Returns true if
	 * everything is looking good. Otherwise false.
	 * 
	 * @private
	 * @param {Object}
	 *            data - The data object containting the login data.
	 * @method Bestia.Connection#checkLoginData
	 * @returns TRUE if all the login data is existing. FALSE if there is something
	 *          missing or an error.
	 */
	_checkLoginData(data) {

		var state = true;

		if (!data) {
			LOG.error('No login data present.');
			state = false;
		}

		if (!state || data.token === undefined) {
			LOG.error('Login: token missing.');
			state = false;
		} else if (!state | data.accId === undefined) {
			LOG.error('Login: account id missing.');
			state = false;
		} else if (!state | data.username === undefined) {
			LOG.error('Login: username missing.');
			state = false;
		}

		if (state === false) {
			this._pubsub.publish(Signal.IO_AUTH_ERROR);
			return false;
		}

		return true;
	}
}


	
