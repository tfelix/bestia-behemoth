Bestia.Chat.Commands.HelpCommand = function() {
	this.paramRegex = /\/help/gi;
	this.cmdRegex = /\/help/gi;

	this.cmdHandle = 'help';
};

Bestia.Chat.Commands.HelpCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.HelpCommand.prototype.constructor = Bestia.Chat.Commands.HelpCommand;

Bestia.Chat.Commands.HelpCommand.prototype._doCommand = function(cmdStr, chat, game) {
	// Iterate over all registered commands and get their help text.
	$(chat._localCommands).each(function(key, value){
		value._shortHelp(chat);
		value._help(chat);
	});
}
