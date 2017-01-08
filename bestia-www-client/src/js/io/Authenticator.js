/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import Signal from './Signal';
import Urls from '../Urls';
import MID from './messages/MID';

/**
 * Class which reads the login token from a storage system and tries to
 * authenticate with the server upon a connection.
 */
export default class Authenticator {
	
	constructor(pubsub) {
		
		this._pubsub = pubsub;
		
		this._pubsub.subscribe(Signal.IO_CONNECTED, this._onConnected, this);
		this._pubsub.subscribe(MID.SYSTEM_AUTHREPLY, this._onAuthReply, this);
	}
	
	/**
	 * Sends auth message if the system is connected.
	 */
	_onConnected() {
		// Prepare login message and send it.
		var loginMsg = {
			mid: 'system.loginauth',
			accId : 1,
			token : '04473c9f-65e9-4f59-9075-6da257a21826'
		};
		this._pubsub.send(loginMsg);
	}
	
	/**
	 * Reads the auth reply from the server.
	 */
	_onAuthReply(_, msg) {
		if(msg.s === 'ACCEPTED') {
			this._pubsub.publish(Signal.IO_AUTH_CONNECTED);
		} else {
			this._pubsub.publish(Signal.IO_AUTH_ERROR);
			// Go to login if there is wrong data.
			window.location.replace(Urls.loginHtml);
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
			console.error('No login data present.');
			state = false;
		}

		if (!state || data.token === undefined) {
			console.error('Login: token missing.');
			state = false;
		} else if (!state | data.accId === undefined) {
			console.error('Login: account id missing.');
			state = false;
		} else if (!state | data.username === undefined) {
			console.error('Login: username missing.');
			state = false;
		}

		if (state === false) {
			this._pubsub.publish(Signal.IO_AUTH_ERROR);
			return false;
		}

		return true;
	}
}


	
