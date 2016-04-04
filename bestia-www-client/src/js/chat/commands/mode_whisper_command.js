Bestia.Chat.Commands = Bestia.Chat.Commands || {};

Bestia.Chat.Commands.ModeWhisperCommand = function() {

	this._whisperRegex = /^\/[wW] (\w.+) /;
};

Bestia.Chat.Commands.ModeWhisperCommand.prototype = new Bestia.Chat.Commands.RealtimeCommand();
Bestia.Chat.Commands.ModeWhisperCommand.prototype.constructor = Bestia.Chat.Commands.ModeWhisperCommand;


Bestia.Chat.Commands.ModeWhisperCommand.prototype.isCommand = function(text, chat) {

	if (this._whisperRegex.test(text)) {
		chat.whisperNick(RegExp.$1);
		chat.text(text.replace(this._whisperRegex, ''));
	}

};
