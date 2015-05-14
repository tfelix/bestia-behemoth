/**
 * View Model for single messages.
 * 
 * @param msg
 * @returns
 */
function ChatMessageModel(msg) {
	var self = this;

	self.nickname = ko.observable(msg.sn);
	self.mode = ko.observable(msg.m);
	self.text = ko.observable(msg.txt);

	self.cssMode = ko.pureComputed(function() {
		return self.mode().toLowerCase();
	});
}

function ChatViewModel(localNickname) {
	var self = this;

	// Config values.
	self.MAX_MESSAGES = 50;
	self.localNickname = localNickname;

	self.mode = ko.observable('PUBLIC');
	self.modeText = ko.computed(function() {
		// TODO Lokalisieren
		if (self.mode() == 'PUBLIC') {
			return "Öffentlich";
		}
		if (self.mode() == 'PARTY') {
			return "Party";
		}
		if (self.mode() == 'GUILD') {
			return "Gilde";
		}
	});
	self.whisperNick = ko.observable('');
	self.messages = ko.observableArray();
	self.text = ko.observable('');

	/**
	 * Changes the mode of the model.
	 */
	self.changeMode = function(mode) {
		self.mode(mode);
	};

	// TODO Die Commands in eigene Objekte refactoren.
	/**
	 * Identifying local commands which are executed when the user presses
	 * enter.
	 * 
	 * @returns TRUE if the message was a local command and was executed. FALSE
	 *          if the local command was not found. Message is for the server.
	 */
	self.executeLocalCommand = function(str) {
		if (str.startsWith('/clear')) {
			self.messages.removeAll();
			self.text('');
			return true;
		}

		return false;
	};

	/**
	 * Triggers if a chat is about to be send. Create a chat message of all the
	 * typed information and fire it to the server.
	 */
	self.sendChat = function() {
		var msgText = self.text();

		// Clear text.
		self.text('');

		if (self.executeLocalCommand(msgText)) {
			return;
		}

		var msg = new app.message.Chat(self.mode(), msgText, self.whisperNick(), self.localNickname);

		$.publish('io.sendMessage', msg);
		self.addMessage(msg);
	};

	self.setWhisperNick = function(message) {
		self.whisperNick(message.nickname());
	};
}

/**
 * Adds a message to the chat model.
 */
ChatViewModel.prototype.addMessage = function(msg) {
	this.messages.push(new ChatMessageModel(msg));

	if (this.messages().length > this.MAX_MESSAGES) {
		this.messages.shift();
	}
};