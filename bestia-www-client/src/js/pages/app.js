/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

import Urls from '../Urls.js';

/**
 * This file holds classes to manage the UI logic. They wire inside the event
 * system to listen for server messages and perform UI actions upon arrival of
 * these messages.
 */


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
class LogoutDialog {
	constructor(domID, pubsub) {

		this.dialog = $(domID);
		if (this.dialog.length === 0) {
			throw "DOM node was not found.";
		}

		this.dialog.on('hide.bs.modal', function() {
			window.location.replace(Urls.loginHtml);
		});

		pubsub.subscribe('system.logout', $.proxy(this._handleLogout, this));
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

export { LogoutDialog };