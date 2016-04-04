Bestia.Chat.Commands = Bestia.Chat.Commands || {};


Bestia.Chat.Commands.ModePartyCommand = function() {

	this.commandStr = '/p ';
};

Bestia.Chat.Commands.ModePartyCommand.prototype = new Bestia.Chat.Commands.RealtimeCommand();
Bestia.Chat.Commands.ModePartyCommand.prototype.constructor = Bestia.Chat.Commands.ModePartyCommand;


Bestia.Chat.Commands.ModePartyCommand.prototype._doCommand = function(text, chat) {

	chat.mode('PARTY');
	chat.text(text.replace(this.commandStr, ''));
	chat.whisperNick('');
	
};
