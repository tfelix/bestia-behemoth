Bestia.Chat.Commands.HelpCommand = function() {
	this.paramRegex = /\/help/gi;
	this.cmdRegex = /\/help/gi;

	this.shortHelp = i18n.t("chat.commands.help_short");
	this.help = i18n.t("chat.commands.help");
};

Bestia.Chat.Commands.HelpCommand.prototype = new Bestia.Chat.Commands.BasicCommand();
Bestia.Chat.Commands.HelpCommand.prototype.constructor = Bestia.Chat.Commands.HelpCommand;

Bestia.Chat.Commands.HelpCommand.prototype._doCommand = function(cmdStr, chat, game) {
	// no op.
	console.log("Hello World");
}
