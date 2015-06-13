/**
 * Toggles between the debug mode of the game.
 * 
 * Usage: /debug ON|OFF
 * 
 * @returns TRUE if the chat string starts with this command. FALSE otherwise.
 */
Bestia.Chat.Commands.DebugCommand = function() {
	this.cmdRegex = /\/debug/i;
	this.paramRegex = /\/debug (ON|OFF)/i;
	this.cmdHandle = 'debug';
};

Bestia.Chat.Commands.DebugCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.DebugCommand.prototype.constructor = Bestia.Chat.Commands.DebugCommand;

Bestia.Chat.Commands.DebugCommand.prototype._doCommand = function(cmdStr, chat, game) {
	if(this.matches[1].toUpperCase() === 'ON') {
		game.config.debug(true);
	} else {
		game.config.debug(false);
	}
};