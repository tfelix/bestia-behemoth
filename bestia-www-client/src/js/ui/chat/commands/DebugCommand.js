import BasicCommand from './BasicCommand.js';
import LOG from '../../../util/Log';
import Signal from '../../../io/Signal';

/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
export default class DebugCommand extends BasicCommand {
	constructor() {
		super();

		this.cmdRegex = /\/debug/i;
		this.paramRegex = /\/debug (.*)/i;
		this.cmdHandle = 'debug';
	}
	
	_doCommand(cmdStr, chat, pubsub) {
		let command = this.matches[1];
		LOG.debug('Received debug command:', command);
		pubsub.publish(Signal.ENGINE_DEBUG_CMD, command);
	}
}

