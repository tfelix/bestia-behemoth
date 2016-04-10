import BasicCommand from './BasicCommand.js';

/**
 * Prints the help of all registered commands.
 */
export default class HelpCommand extends BasicCommand {
	constructor() {
		super();
		
		this.paramRegex = /\/help/i;
		this.cmdRegex = /\/help/i;
		this.cmdHandle = 'help';
	}
	
	_doCommand(cmdStr, chat) {
		// Iterate over all registered commands and get their help text.
		$(chat._localCommands).each(function(key, value){
			value._shortHelp(chat);
			value._help(chat);
		});
	}
}

