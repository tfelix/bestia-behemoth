(function(app, $, ko) {
	'use strict';

	// Add startsWith functionality to string prototype.
	if (typeof String.prototype.startsWith != 'function') {
		String.prototype.startsWith = function(str) {
			return this.slice(0, str.length) == str;
		};
	}

	/*
	 * modes PUBLIC, PARTY, GUILD, WHISPER, SYSTEM, GM_BROADCAST, ERROR, COMMAND
	 */

	function ChatMessageModel(msg) {
		var self = this;
		
		self.nickname = ko.observable(msg.sn);
		self.mode = ko.observable(msg.m);
		self.text = ko.observable(msg.txt);
		
		self.cssMode = ko.pureComputed(function(){
			return self.mode().toLowerCase();
		});
	}

	function ChatViewModel() {
		var self = this;

		// Config values.
		self.MAX_MESSAGES = 50;
		
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

		/**
		 * Triggers if a chat is about to be send. Create a chat message of all
		 * the typed information and fire it to the server.
		 */
		self.sendChat = function() {

			// Clear text.
			self.text('');
		};
	}

	/**
	 * Adds a message to the chat model.
	 */
	ChatViewModel.prototype.addMessage = function(msg) {
		this.messages.push(new ChatMessageModel(msg));
		
		if(this.messages().length > this.MAX_MESSAGES) {
			this.messages.shift();
		}
	};

	function Chat(domNode) {
		var self = this;
		self.domNode = domNode;
		self.model = new ChatViewModel();

		/**
		 * Identifying local chat commands which can be executed directly by the
		 * client. Later when this gets more complex we might need to refactor
		 * this as an extra class.
		 */
		self.identifyLocalCommand = function(str) {
			if (str.startsWith('/s ')) {
				// Public chat.
				self.model.mode('PUBLIC');
				self.model.text(self.model.text().replace('/s ', ''));
			} else if (str.startsWith('/p ')) {
				// Party chat.
				self.model.mode('PARTY');
				self.model.text(self.model.text().replace('/p ', ''));
			} else if (str.startsWith('/g ')) {
				// Guild chat.
				self.model.mode('GUILD');
				self.model.text(self.model.text().replace('/g ', ''));
			}
		};

		ko.applyBindings(self.model, domNode);

		// Check for constant updates to this value e.g. if the user is typing
		// to this property. react to certain inputs on the fly.
		self.model.text.subscribe(function(newValue) {
			self.identifyLocalCommand(newValue);
		});
		
		// Finally subscribe to chat messages.
		$.subscribe('chat.message', function(_, msg){
			self.model.addMessage(msg);
		});
	}

	app.Chat = Chat;
})(Bestia, jQuery, ko);