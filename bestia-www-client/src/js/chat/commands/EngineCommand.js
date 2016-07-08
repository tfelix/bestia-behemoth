import BasicCommand from './BasicCommand.js';

/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
export default class EngineCommand extends BasicCommand {
	constructor(brightnessFx) {
		super();
		
		this.cmdRegex = /\/engine/i;
		this.paramRegex = null;
		this.cmdHandle = 'engine';
		
		this._brightnessFx = brightnessFx;
	}
	
	_doCommand(cmdStr) {
		
		var strs = cmdStr.split(' ');		
		var value = parseFloat(strs[1]);
		
		this._brightnessFx.brightness = value;
	}
}

