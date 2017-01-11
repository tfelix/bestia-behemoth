import BasicCommand from './BasicCommand.js';

/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
export default class DebugCommand extends BasicCommand {
	constructor() {
		this.cmdRegex = /\/debug/i;
		this.paramRegex = /\/debug (ON|OFF)/i;
		this.cmdHandle = 'debug';
	}
	
	_doCommand(cmdStr, chat, game) {
		if(this.matches[1].toUpperCase() === 'ON') {
			game.config.debug(true);
		} else {
			game.config.debug(false);
		}
	}
}

