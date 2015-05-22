/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

function strStartsWith(str, start) {
	return str.slice(0, start.length) == start;
}

/**
 * Chat for the bestia client. It subscribes to the necessairy messages to get
 * informed if new messages arrive. Updates and displays the messages. Will
 * provide chat functionality to the user.
 * 
 * @class Bestia.Chat
 * @param {BestiaGame}
 *            game - An instance to the central bestia game object.
 * @param {String}
 *            localNickname - How the players account is called to display the
 *            correct name in the echo messages.
 */
Bestia.Chat = function(game, localNickname) {
	var self = this;

	/**
	 * Number of max messages until old messages are discarded.
	 * 
	 * @property
	 * @contant
	 */
	this.MAX_MESSAGES = 50;
	this.localNickname = localNickname;

	/**
	 * Array list of local commands. Commands can register themselve to this
	 * array and they will be executed when a message is typed so the can
	 * interact with the user. For a sample implementation look inside the
	 * command folder.
	 */
	this._localCommands = [];
	this.whisperRegex = /^\/[wW] (\w.+) /;
	this.game = game;

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
	/**
	 * Holds the nickname which is used to whisper someone.
	 * @property
	 */
	self.whisperNick = ko.observable('');
	
	/**
	 * Holds all the messages for the chat.
	 * @property
	 */
	self.messages = ko.observableArray();
	
	/**
	 * Holds the text of the chat.
	 */
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

	/**
	 * Triggers if a chat is about to be send. Create a chat message of all the
	 * typed information and fire it to the server.
	 */
	self.sendChat = function() {
		var msgText = self.text();

		// Clear text.
		self.text('');

		var localCmd = this._localCommands;
		for (var i = 0; i < localCmd.length; i++) {
			if (localCmd[i].executeCommand(msgText, self, self.game) === true) {
				// Command was handled.
				return;
			}
		}

		var msg = new Bestia.Message.Chat(self.mode(), msgText, self.whisperNick(), self.localNickname);

		Bestia.publish('io.sendMessage', msg);
		self.addMessage(msg);
	};

	self.setWhisperNick = function(message) {
		self.whisperNick(message.nickname());
	};

	// Register all local command handler.
	$.each(Bestia.Chat.Commands, function(key, Command) {
		// BasicCommand is kind of a abstract placeholder. It has no use. Skip
		// it.
		if (key === 'BasicCommand') {
			return;
		}
		self._localCommands.push(new Command());
	});

	// Finally subscribe to chat messages.
	Bestia.subscribe('chat.message', function(_, msg) {
		self.addMessage(msg);
	});

};

/**
 * Adds a message to the chat model. Message is from the server and therefore a
 * server object.
 */
Bestia.Chat.prototype.addMessage = function(msg) {
	this.messages.push(new Bestia.ChatMessage(msg));

	if (this.messages().length > this.MAX_MESSAGES) {
		this.messages.shift();
	}
};

/**
 * Locally add a message from a script event to display it to the user.
 * 
 * @method Bestia.Chat#addLocalMessage
 * @param {String}
 *            msg - Textstring to add to the chat.
 * @param {String}
 *            mode - Mode/Type of the message to add. Possible types are:
 *            PUBLIC, PARTY, GUILD, WHISPER, SYSTEM, GM_BROADCAST, ERROR,
 *            COMMAND.
 */
Bestia.Chat.prototype.addLocalMessage = function(msg, mode) {
	var msgObj = new Bestia.Message.Chat(mode, msg);
	this.addMessage(msgObj);
};

/**
 * Identifying local chat commands which can be executed directly by the client.
 * Later when this gets more complex we might need to refactor this as an extra
 * class.
 */
Bestia.Chat.prototype._identifyLocalCommandTyping = function(str) {

	// TODO Das hier kann auch noch ausgelagert werden wenn ich eine performante
	// lösung habe.
	// TODO hier kann man unterscheiden zwischen einem live command und einem
	// normalen command welches erst nach dem absenden überprüft wird.
	if (strStartsWith(str, '/s ')) { // Public chat.
		this.mode('PUBLIC');
		this.text(str.replace('/s ', ''));
		this.whisperNick('');
	} else if (strStartsWith(str, '/p ')) { // Party chat.
		this.mode('PARTY');
		this.text(str.replace('/p ', ''));
		this.whisperNick('');
	} else if (strStartsWith(str, '/g ')) { // Guild chat.
		this.mode('GUILD');
		this.text(str.replace('/g ', ''));
		this.whisperNick('');
	} else if (this.whisperRegex.test(str)) { // Whisper chat.
		this.whisperNick(RegExp.$1);
		this.text(str.replace(this.whisperRegex, ''));
	}
};

/**
 * Holds all objects for defined local chat commands.
 * 
 * @namespace Bestia.Chat.Commands
 */
Bestia.Chat.Commands = {};
