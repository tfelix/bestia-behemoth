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
 * @param {DOMElement}
 *            domEle - DOM element of the chat. Should have all the needed
 *            classes of a chat set.
 * @param {BestiaGame}
 *            game - An instance to the central bestia game object.
 * @param {String}
 *            localNickname - How the players account is called to display the
 *            correct name in the echo messages.
 */
Bestia.Chat = function(domEle, game, localNickname) {
	var self = this;

	/**
	 * Number of max messages until old messages are discarded.
	 * 
	 * @property
	 * @constant
	 */
	this.MAX_MESSAGES = 50;
	this.localNickname = localNickname;

	this.domEle = domEle;
	this.chatEle = $(domEle).find('.chat-msgs:first').get(0);

	/**
	 * Array list of local commands. Commands can register themselve to this
	 * array and they will be executed when a message is typed so the can
	 * interact with the user. For a sample implementation look inside the
	 * command folder.
	 */
	this._localCommands = [];
	this.whisperRegex = /^\/[wW] (\w.+) /;
	this.game = game;

	this.mode = ko.observable('PUBLIC');
	this.modeText = ko.computed(function() {
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
	 * 
	 * @property
	 */
	this.whisperNick = ko.observable('');

	/**
	 * Holds all the messages for the chat.
	 * 
	 * @property
	 */
	this.messages = ko.observableArray();

	/**
	 * Flag if the chat as unread messages for the user at the bottom of the
	 * display.
	 * 
	 * @property {boolean}
	 */
	this.hasUnreadMessages = ko.observable(false);

	/**
	 * Holds the text of the chat.
	 */
	this.text = ko.observable('');

	// Check for constant updates to this value e.g. if the user is typing
	// to this property. react to certain inputs on the fly.
	this.text.subscribe(function(newValue) {
		self._identifyLocalCommandTyping(newValue);
	});

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
 * Triggers if a chat is about to be send. Create a chat message of all the
 * typed information and send it to the server.
 * 
 * @public
 * @method Bestia.Chat#sendChat
 */
Bestia.Chat.prototype.sendChat = function() {
	var msgText = this.text();

	// Clear text.
	this.text('');

	var localCmd = this._localCommands;
	for (var i = 0; i < localCmd.length; i++) {
		if (localCmd[i].executeCommand(msgText, this, this.game) === true) {
			// Command was handled.
			return;
		}
	}

	var msg = new Bestia.Message.Chat(this.mode(), msgText, this.whisperNick(), this.localNickname);

	Bestia.publish('io.sendMessage', msg);
	this.addMessage(msg);
};

/**
 * Changes the mode of the model. Is used to bind against as a view callback.
 * 
 * @public
 * @method Bestia.Chat#changeMode
 */
Bestia.Chat.prototype.changeMode = function(mode) {
	this.mode(mode);
};

/*
 * Bestia.Chat.prototype.setWhisperNick = function(message) {
 * self.whisperNick(message.nickname()); };
 */

/**
 * Adds a message to the chat model. Message is from the server and therefore a
 * server object. If scrolled to bottom stays at bottom. If not display a
 * notification that there are new messages waiting.
 * 
 * @method Bestia.Chat#addMessage
 * @param {Bestia.ChatMessage}
 *            msg - Chat message to add to the display.
 * @public
 */
Bestia.Chat.prototype.addMessage = function(msg) {

	var scrollBottom = false;
	var scrollPos = this.chatEle.scrollTop + this.chatEle.clientHeight;
	// Check bottom.
	if (scrollPos === this.chatEle.scrollHeight) {
		scrollBottom = true;
	}

	this.messages.push(new Bestia.ChatMessage(msg));

	if (this.messages().length > this.MAX_MESSAGES) {
		this.messages.shift();
	}

	if (scrollBottom) {
		this.scrollToBottom();
	} else {
		this.hasUnreadMessages(true);
	}
};

/**
 * Scrolls to the latest messages in the chat and sets the flag
 * {@code hasUnreadMessages} to false.
 * 
 * @method Bestia.Chat#scrollToBottom
 * @public
 */
Bestia.Chat.prototype.scrollToBottom = function() {
	this.chatEle.scrollTop = this.chatEle.scrollHeight;
	this.hasUnreadMessages(false);
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
