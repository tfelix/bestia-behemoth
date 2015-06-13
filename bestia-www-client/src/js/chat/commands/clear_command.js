/**
 * Clears the chat when the command gets executed.
 * 
 * Usage: /clear
 * 
 */
Bestia.Chat.Commands.ClearCommand = function() {
	this.paramRegex = /\/clear/i;
	this.cmdRegex = /\/clear/i;

	this.cmdHandle = 'clear';
};

Bestia.Chat.Commands.ClearCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.ClearCommand.prototype.constructor = Bestia.Chat.Commands.ClearCommand;

Bestia.Chat.Commands.ClearCommand.prototype._doCommand = function(cmdStr, chat) {
	chat.messages.removeAll();
};