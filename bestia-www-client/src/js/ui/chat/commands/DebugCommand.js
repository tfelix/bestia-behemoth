import BasicCommand from './BasicCommand.js';
import LOG from '../../../util/Log';
import Signal from '../../../io/Signal';

/**
 * Can be used to send debug commands directly to the engine.
 * 
 * Usage: /debug weather.rain 0.5
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

		// Transform the debug command into an array.
		let commandData = command.split('.');

		LOG.debug('Received debug command:', command);
		pubsub.publish(Signal.ENGINE_DEBUG_CMD, commandData);
	}
}

