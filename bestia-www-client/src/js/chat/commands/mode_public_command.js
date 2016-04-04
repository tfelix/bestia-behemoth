Bestia.Chat.Commands = Bestia.Chat.Commands || {};


Bestia.Chat.Commands.ModePublicCommand = function() {

	this.commandStr = '/s ';
};

Bestia.Chat.Commands.ModePublicCommand.prototype = new Bestia.Chat.Commands.RealtimeCommand();
Bestia.Chat.Commands.ModePublicCommand.prototype.constructor = Bestia.Chat.Commands.ModePublicCommand;


Bestia.Chat.Commands.ModePublicCommand.prototype._doCommand = function(text, chat) {

	chat.mode('PUBLIC');
	chat.text(text.replace('/s ', ''));
	chat.whisperNick('');
	
};
