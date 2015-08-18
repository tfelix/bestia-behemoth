/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * This object holds classes to manage the UI logic. They wire inside the event
 * system to listen for server messages and perform UI actions upon arrival of
 * these messages.
 * 
 * @namespace Bestia.Page
 */
Bestia.Page = Bestia.Page || {};

/**
 * Listens for 'system.logout' messages and perform a user notification and
 * logout process.
 * 
 * @class Bestia.Page.LogoutDialog
 * @constructor
 * @param {String}
 *            domID - DOM id of the object which will be used as the bootstrap
 *            modal dialog popup.
 */
Bestia.Page.LogoutDialog = function(domID) {

	this.dialog = $(domID);

	if (this.dialog.length === 0) {
		throw "DOM node was not found.";
	}

	this.dialog.on('hide.bs.modal', function() {
		window.location.replace(Bestia.Urls.loginUrl);
	});

	Bestia.subscribe('system.logout', $.proxy(this._handleLogout, this));
};

/**
 * Handler for the logout system message.
 * @private
 * @method Bestia.Page.LogoutDialog#_handleLogout
 */
Bestia.Page.LogoutDialog.prototype._handleLogout = function() {
	this.dialog.modal('show');
};
