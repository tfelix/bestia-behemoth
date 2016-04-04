Bestia.Chat.Commands = Bestia.Chat.Commands || {};

/**
 * Prints the help of all registered commands.
 */
Bestia.Chat.Commands.HelpCommand = function() {
	this.paramRegex = /\/help/i;
	this.cmdRegex = /\/help/i;

	this.cmdHandle = 'help';
};

Bestia.Chat.Commands.HelpCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.HelpCommand.prototype.constructor = Bestia.Chat.Commands.HelpCommand;

Bestia.Chat.Commands.HelpCommand.prototype._doCommand = function(cmdStr, chat) {
	// Iterate over all registered commands and get their help text.
	$(chat._localCommands).each(function(key, value){
		value._shortHelp(chat);
		value._help(chat);
	});
};
