(function(app, $, ko) {
	'use strict';

	// Add startsWith functionality to string prototype.
	if (typeof String.prototype.startsWith != 'function') {
		String.prototype.startsWith = function(str) {
			return this.slice(0, str.length) == str;
		};
	}

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

		// TODO Die Local Commands und Commands allgemein können und sollten
		// refactored werden in einzelne Objekte um sie besser zu trennen.

		/**
		 * Identifying local commands which are executed when the user presses
		 * enter.
		 * 
		 * @returns TRUE if the message was a local command and was executed.
		 *          FALSE if the local command was not found. Message is for the
		 *          server.
		 */
		self.executeLocalCommand = function(str) {
			if (str.startsWith('/clear')) {
				self.messages.removeAll();
				self.text('');
				return true;
			}

			return false;
		}

		/**
		 * Triggers if a chat is about to be send. Create a chat message of all
		 * the typed information and fire it to the server.
		 */
		self.sendChat = function() {
			var msgText = self.text();

			// Clear text.
			self.text('');

			if (self.executeLocalCommand(msgText)) {
				return;
			}

			var msg = new app.message.Chat(self.mode(), msgText, self
					.whisperNick(), self.localNickname);

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

	/**
	 * Chat module. Contains the ViewModel for a chat and some functions to
	 * check the various commands.
	 */
	function Chat(domNode, localNick) {
		var self = this;
		self.domNode = domNode;
		self.model = new ChatViewModel(localNick);

		var whisperRegex = /^\/[wW] (\w.+) /;

		/**
		 * Identifying local chat commands which can be executed directly by the
		 * client. Later when this gets more complex we might need to refactor
		 * this as an extra class.
		 */
		self.identifyLocalCommandTyping = function(str) {
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
			self.identifyLocalCommandTyping(newValue);
		});

		// Finally subscribe to chat messages.
		$.subscribe('chat.message', function(_, msg) {
			self.model.addMessage(msg);
		});
	}

	app.Chat = Chat;
})(Bestia, jQuery, ko);