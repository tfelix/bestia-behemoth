
Bestia.Chat.Commands.BasicCommand = function() {
	this.cmdRegex = new RegExp();
	this.paramRegex = new RegExp();

	this.cmdHandle = '';
	this.matches = null;
};

Bestia.Chat.Commands.BasicCommand.prototype._checkCommand = function(cmdStr, chat, game) {
	return this.cmdRegex.test(cmdStr);
};

Bestia.Chat.Commands.BasicCommand.prototype._checkParameter = function(cmdStr) {
	this.matches = this.paramRegex.exec(cmdStr);
	return this.matches !== null;
};

Bestia.Chat.Commands.BasicCommand.prototype.executeCommand = function(cmdStr, chat, game) {

	if (!this._checkCommand(cmdStr, chat, game)) {
		return false;
	}

	// Command looks good. Now try to check the parameter.
	if (!this._checkParameter(cmdStr)) {
		// Parameter wrong. Display help.
		this._shortHelp(chat);
		this._help(chat);
		// Command was "handled".
		return true;
	}
	
	this._doCommand(cmdStr, chat, game);
	return true;
};

/**
 * Prints a short help notice.
 * 
 * @method Bestia.Chat.Commands.ChatCommand#shortHelp
 * @return void
 */
Bestia.Chat.Commands.BasicCommand.prototype._shortHelp = function(chat) {	
	chat.addLocalMessage(i18n.t('chat.commands.'+this.cmdHandle+'_short'), 'SYSTEM');
};

/**
 * Prints a longer help notice.
 * 
 * @method Bestia.Chat.Commands.BasicCOmmand#_help()
 * @param {Bestia.Chat}
 *            chat - Chat instance.
 */
Bestia.Chat.Commands.BasicCommand.prototype._help = function(chat) {
	chat.addLocalMessage(i18n.t('chat.commands.'+this.cmdHandle), 'SYSTEM');
};

Bestia.Chat.Commands.BasicCommand.prototype._doCommand = function(cmdStr, chat, game) {
	// no op.
}