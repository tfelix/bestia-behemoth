import BasicCommand from './BasicCommand.js';
import { strStartsWith } from './../../util/Utils.js';

/**
 * This kinds of commands are checked agains in real time while the user is
 * typing. Therefore the check needs to be very performant not to slow down
 * because the isCommand method is called on each keystroke.
 * 
 * @class Bestia.Chat.Commands.RealtimeCommand
 */
export default class RealtimeCommand extends BasicCommand {
	
	constructor() {
		/**
		 * The string which triggers the command. As soon as the string is found at
		 * the beginning of a typed text the command is executed.
		 * 
		 * @public
		 * @constant
		 */
		this.commandStr = '';
	}

	/**
	 * Checks if the command string matches the input. If this is the case the
	 * command is executed right away.
	 * 
	 * @public
	 * @method Bestia.Chat.Commands.RealtimeCommand.prototype#isCommand
	 * @param text
	 *            {string} - Text of the chat to check agains the command.
	 * @param chat
	 *            {Bestia.Chat} - Chat instance to to some work.
	 * @return TRUE if the command string matches the command in the text. FALSE
	 *         otherwise.
	 */
	isCommand(text, chat) {
		if (this.commandStr.length === 0) {
			// CMD string must be set.
			return false;
		}
		if (strStartsWith(text, this.commandStr)) {
			this._doCommand(text, chat);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Overwrite in child implementations to execute the command.
	 * 
	 * @private
	 * @param chat
	 *            {Bestia.Chat} - Chat instance to to some work.
	 */
	_doCommand() {
		// no op.
	}
}
