Bestia.Chat.Commands = Bestia.Chat.Commands || {};

/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
Bestia.Chat.Commands.EngineCommand = function(brightnessFx) {
	this.cmdRegex = /\/engine/i;
	this.paramRegex = null;
	this.cmdHandle = 'engine';
	
	this._brightnessFx = brightnessFx;
};

Bestia.Chat.Commands.EngineCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.EngineCommand.prototype.constructor = Bestia.Chat.Commands.EngineCommand;

Bestia.Chat.Commands.EngineCommand.prototype._doCommand = function(cmdStr, chat, game) {
	
	var strs = cmdStr.split(' ');
	
	var value = parseFloat(strs[1]);
	
	this._brightnessFx.brightness = value;
};