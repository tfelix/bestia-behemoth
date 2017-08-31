import BasicCommand from './BasicCommand.js';

/**
 * Clears the chat when the command gets executed.
 * 
 * Usage: /clear
 * 
 */
export default class ClearCommand extends BasicCommand {
	constructor() {
		super();
		
		this.paramRegex = /\/clear/i;	
		this.cmdRegex = /\/clear/i;
		this.cmdHandle = 'clear';
	}

	_shortHelp(chat) {
		chat.addLocalMessage('Clears the chat.');
	}

	_help(chat) {
		chat.addLocalMessage('Clears the chat.');
	}
	
	_doCommand(cmdStr, chat) {
		chat.messages.removeAll();
	}
}
