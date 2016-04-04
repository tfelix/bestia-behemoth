Bestia.Chat.Commands = Bestia.Chat.Commands || {};


Bestia.Chat.Commands.ModeGuildCommand = function() {

	this.commandStr = '/g ';
};

Bestia.Chat.Commands.ModeGuildCommand.prototype = new Bestia.Chat.Commands.RealtimeCommand();
Bestia.Chat.Commands.ModeGuildCommand.prototype.constructor = Bestia.Chat.Commands.ModeGuildCommand;


Bestia.Chat.Commands.ModeGuildCommand.prototype._doCommand = function(text, chat) {

	chat.mode('GUILD');
	chat.text(text.replace(this.commandStr, ''));
	chat.whisperNick('');
	
};
