// Add startsWith functionality to string prototype.
/*jshint -W121 */
if (typeof String.prototype.startsWith != 'function') {
	String.prototype.startsWith = function(str) {
		return this.slice(0, str.length) == str;
	};
}

/**
 * Chat module. Contains the ViewModel for a chat and some functions to check
 * the various commands.
 */
Bestia.Chat = function(domNode, localNick) {
	var self = this;
	this.domNode = domNode;
	this.model = new Bestia.ChatViewModel(localNick);
	this._commandHandler = [];

	var whisperRegex = /^\/[wW] (\w.+) /;

	/**
	 * Identifying local chat commands which can be executed directly by the
	 * client. Later when this gets more complex we might need to refactor this
	 * as an extra class.
	 */
	this._identifyLocalCommandTyping = function(str) {
		if (str.startsWith('/s ')) {
			// Public chat.
			self.model.mode('PUBLIC');
			self.model.text(str.replace('/s ', ''));
			self.model.whisperNick('');
		} else if (str.startsWith('/p ')) {
			// Party chat.
			self.model.mode('PARTY');
			self.model.text(str.replace('/p ', ''));
			self.model.whisperNick('');
		} else if (str.startsWith('/g ')) {
			// Guild chat.
			self.model.mode('GUILD');
			self.model.text(str.replace('/g ', ''));
			self.model.whisperNick('');
		} else if (whisperRegex.test(str)) {
			// Whisper chat.
			self.model.whisperNick(RegExp.$1);
			self.model.text(str.replace(whisperRegex, ''));
		}
	};

	ko.applyBindings(self.model, domNode);

	// Check for constant updates to this value e.g. if the user is typing
	// to this property. react to certain inputs on the fly.
	self.model.text.subscribe(function(newValue) {
		self._identifyLocalCommandTyping(newValue);
	});

	// Finally subscribe to chat messages.
	$.subscribe('chat.message', function(_, msg) {
		self.model.addMessage(msg);
	});
};

/*
Bestia.Chat.registerCommand = function(cmd) {
	
};*/
