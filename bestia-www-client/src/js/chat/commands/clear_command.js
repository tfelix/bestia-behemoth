/**
 * Clears the chat when the command gets executed.
 * 
 * Usage: /clear
 * 
 */
Bestia.Chat.Commands.ClearCommand = function() {
	this.paramRegex = /\/clear/gi;
	this.cmdRegex = /\/clear/gi;

	this.cmdHandle = 'clear';
};

Bestia.Chat.Commands.ClearCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.ClearCommand.prototype.constructor = Bestia.Chat.Commands.ClearCommand;

Bestia.Chat.Commands.ClearCommand.prototype._doCommand = function(cmdStr, chat, game) {
	chat.messages.removeAll();
}