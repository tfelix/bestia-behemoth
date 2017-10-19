/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

import Urls from '../Urls.js';
import Signal from '../io/Signal.js';

/**
 * Listens for 'system.logout' messages and perform a user notification and
 * logout process.
 * 
 * @class Bestia.Page.LogoutDialog
 * @constructor
 * @param {String}
 *            domID - DOM id of the object which will be used as the bootstrap
 *            modal dialog popup.
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
export default class LogoutDialog {
	constructor(domID, pubsub) {

		this.dialog = $(domID);
		if (this.dialog.length === 0) {
			throw 'DOM node was not found.';
		}

		this.dialog.on('hide.bs.modal', function() {
			window.location.replace(Urls.loginHtml);
		});

		pubsub.subscribe(Signal.SYSTEM_LOGOUT, this._handleLogout.bind(this));
	}
	
	/**
	 * Handler for the logout system message.
	 * 
	 * @private
	 * @method Bestia.Page.LogoutDialog#_handleLogout
	 */
	_handleLogout() {
		this.dialog.modal('show');
	}
}