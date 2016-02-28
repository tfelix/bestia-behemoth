Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * The dialog manager will listen for dialog messages from the server and display them to the user.
 * It is also responsible for sending back the response to the server if the user 
 * is able to interact with this dialog.
 * For some dialogs it might be needed to access the game and find the entity to which it belongs in
 * order to position it correctly.
 * 
 * @class Bestia.Engine.FX.Dialog
 */
Bestia.Engine.FX.Dialog = function(pubsub, game) {

	this._game = game;
	
	this._pubsub = pubsub;

	pubsub.subscribe(Bestia.MID.UI_DIALOG, this.handleMessage.bind(this));
};

/**
 * Handles the incoming dialog message.
 * 
 * @private
 * @param _
 * @param msg
 */
Bestia.Engine.FX.Dialog.prototype.handleMessage = function(_, msg) {
	console.debug("Show dialog message.");
};