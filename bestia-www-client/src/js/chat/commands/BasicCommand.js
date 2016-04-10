/**
 * Sample command which should be extended when writing new functions for the
 * local chat.
 * 
 * @class Bestia.Chat.Commands.BestiaCommand
 */
export default class BasicCommand {
	
	constructor() {
	/**
	 * Regex to detect the command itself.
	 * 
	 * @public
	 * @constant
	 */
	this.cmdRegex = new RegExp();
	this.paramRegex = new RegExp();

	this.cmdHandle = '';
	this.matches = null;
}
	
	/**
	 * Checks if the command matches the input.
	 * 
	 * @private
	 * @method Bestia.Chat.Commands.BestiaCommand#_checkCommand
	 * @return TRUE if the command matches. FALSE if the command does not match.
	 */
	_checkCommand(cmdStr) {
		return this.cmdRegex.test(cmdStr);
	}

	/**
	 * Checks the parameter of the commands.
	 * 
	 * @private
	 * @method _checkParameter
	 * @returns FALSE is the parameter of the command are not correct. TRUE
	 *          otherwise.
	 */
	_checkParameter(cmdStr) {
		if(this.paramRegex === null) {
			return true;
		}
		this.matches = this.paramRegex.exec(cmdStr);
		return this.matches !== null;
	}

	executeCommand(cmdStr, chat, game) {

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
	}

	/**
	 * Prints a short help notice.
	 * 
	 * @method Bestia.Chat.Commands.ChatCommand#shortHelp
	 * @return void
	 */
	_shortHelp(chat) {
		chat.addLocalMessage(i18n.t('chat.commands.' + this.cmdHandle + '_short'), 'SYSTEM');
	}

	/**
	 * Prints a longer help notice.
	 * 
	 * @method Bestia.Chat.Commands.BasicCommand#_help()
	 * @param {Bestia.Chat}
	 *            chat - Chat instance.
	 */
	_help(chat) {
		chat.addLocalMessage(i18n.t('chat.commands.' + this.cmdHandle), 'SYSTEM');
	}

	/**
	 * Overwrite in child implementations to execute the command.
	 * 
	 * @private
	 */
	_doCommand() {
		// no op.
	}
	
}
