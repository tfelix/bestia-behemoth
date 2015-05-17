/**
 * View Model for single messages.
 * 
 * @param msg
 * @returns
 */
Bestia.ChatMessageModel = function(msg) {
	var self = this;

	self.nickname = ko.observable(msg.sn);
	self.mode = ko.observable(msg.m);
	self.text = ko.observable(msg.txt);

	self.cssMode = ko.pureComputed(function() {
		return self.mode().toLowerCase();
	});
};

Bestia.ChatViewModel = function(localNickname) {
	var self = this;

	// Config values.
	this.MAX_MESSAGES = 50;
	this.localNickname = localNickname;
	
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
			self.mode('PUBLIC');
			self.text(str.replace('/s ', ''));
			self.whisperNick('');
		} else if (str.startsWith('/p ')) {
			// Party chat.
			self.mode('PARTY');
			self.text(str.replace('/p ', ''));
			self.whisperNick('');
		} else if (str.startsWith('/g ')) {
			// Guild chat.
			self.mode('GUILD');
			self.text(str.replace('/g ', ''));
			self.whisperNick('');
		} else if (whisperRegex.test(str)) {
			// Whisper chat.
			self.whisperNick(RegExp.$1);
			self.text(str.replace(whisperRegex, ''));
		}
	};

	self.mode = ko.observable('PUBLIC');
	self.modeText = ko.computed(function() {
		if (self.mode() == 'PUBLIC') {
			return i18n.t('chat.public');
		}
		if (self.mode() == 'PARTY') {
			return i18n.t('chat.party');
		}
		if (self.mode() == 'GUILD') {
			return i18n.t('chat.guild');
		}
		
		return i18n.t('chat.public');
	});
	self.whisperNick = ko.observable('');
	self.messages = ko.observableArray();
	self.text = ko.observable('');
	// Check for constant updates to this value e.g. if the user is typing
	// to this property. react to certain inputs on the fly.
	self.text.subscribe(function(newValue) {
		self._identifyLocalCommandTyping(newValue);
	});

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

		var msg = new Bestia.Message.Chat(self.mode(), msgText, self.whisperNick(), self.localNickname);

		Bestia.publish('io.sendMessage', msg);
		self.addMessage(msg);
	};

	
	self.setWhisperNick = function(message) {
		self.whisperNick(message.nickname());
	};
	
	// Finally subscribe to chat messages.
	Bestia.subscribe('chat.message', function(_, msg) {
		self.addMessage(msg);
	});
};

/**
 * Adds a message to the chat model.
 */
Bestia.ChatViewModel.prototype.addMessage = function(msg) {
	this.messages.push(new Bestia.ChatMessageModel(msg));

	if (this.messages().length > this.MAX_MESSAGES) {
		this.messages.shift();
	}
};

/**
 * Adds a message to the chat model.
 */
Bestia.ChatViewModel.prototype.registerCommand = function(cmd) {
	this._commandHandler.push(cmd);
};