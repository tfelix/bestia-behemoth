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
 * @param {string}
 *            LOCAL_NICKNAME - How the players account is called to display the
 *            correct name in the echo messages.
 */
Bestia.Chat = function(domEle, game) {
	var self = this;

	/**
	 * Number of max messages until old messages are discarded.
	 * 
	 * @property
	 * @constant
	 */
	this.MAX_MESSAGES = 50;

	this._pubsub = game.pubsub;

	/**
	 * Name of the bestia master. Is extracted from the game config which is set
	 * during login process.
	 * 
	 * @property
	 * @constant
	 */
	this.LOCAL_NICKNAME = '';

	this._currentBestiaId = 0;

	this.domEle = domEle;

	this.chatEle = $(domEle).find('.chat-msgs:first').get(0);

	/**
	 * Array list of local commands. Commands can register themselve to this
	 * array and they will be executed when a message is typed so the can
	 * interact with the user. For a sample implementation look inside the
	 * command folder.
	 * 
	 * @private
	 * @property
	 */
	this._localCommands = [];

	this.whisperRegex = /^\/[wW] (\w.+) /;
	this.game = game;

	/**
	 * Current chat mode. Possible values are PUBLIC, PARTY or GUILD.
	 * 
	 * @public
	 * @property {String}
	 */
	this.mode = ko.observable('PUBLIC');

	/**
	 * The translated text for the chat mode. Depends upon mode.
	 * 
	 * @public
	 * @property {String}
	 */
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
	 * @property {String}
	 */
	this.whisperNick = ko.observable('');

	/**
	 * Holds all the messages for the chat.
	 * 
	 * @property {Array}
	 */
	this.messages = ko.observableArray();

	/**
	 * Flag if the chat as unread messages for the user at the bottom of the
	 * display.
	 * 
	 * @public
	 * @property {boolean}
	 */
	this.hasUnreadMessages = ko.observable(false);

	/**
	 * Chat an be made visible or invisible.
	 * 
	 * @public
	 * @property {boolean}
	 */
	this.isVisible = ko.observable(false);
	
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
	game.pubsub.subscribe(Bestia.MID.CHAT_MESSAGE, function(_, msg) {
		self.addMessage(msg);
	});

	// Catch authentication to set username for the chat. We can remove ourself
	// once this is done.
	var handleAuthEvent = function(_, data) {
		self.LOCAL_NICKNAME = data.username;
		self.game.pubsub.unsubscribe(Bestia.Signal.AUTH, handleAuthEvent);
	};
	game.pubsub.subscribe(Bestia.Signal.AUTH, handleAuthEvent);

	var handleItemObtainedEvent = function(_, item) {
		i18n.t('chat.item_optained', function(t) {
			var text = t('chat.item_optained').format(item.name(), item.amount());
			self.addLocalMessage(text, "SYSTEM");
		});
	};
	game.pubsub.subscribe(Bestia.Signal.INVENTORY_ITEM_ADD, handleItemObtainedEvent);

	// Handle the selection of a new bestia for the bestia id 
	// (chat messages are input messages).
	game.pubsub.subscribe(Bestia.Signal.BESTIA_SELECTED, function(_, bestia) {
		self._currentBestiaId = bestia.playerBestiaId();
	});
	
	// When the game enters the loading menu hide the chat.
	game.pubsub.subscribe(Bestia.Signal.ENGINE_PREPARE_MAPLOAD, function(){
		self.isVisible(false);
	});
	
	// When the game is displayed also display the chat.
	game.pubsub.subscribe(Bestia.Signal.ENGINE_FINISHED_MAPLOAD, function(){
		self.isVisible(true);
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

	// Prepare and send the message to the server and add it to the local chat.
	var msg = new Bestia.Message.Chat(this.mode(), msgText, this.whisperNick(), this.LOCAL_NICKNAME, self._currentBestiaId);

	// Check if this was a command to be executed on the server and set the
	// message flag accordingly.
	if (msgText.slice(0, 1) === "/") {
		msg.m = 'COMMAND';
	} else {
		// Only add when its no command.
		msg.pbid = this._currentBestiaId;
		this.addMessage(msg);
	}

	this.game.pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);
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

	var chatMsg = new Bestia.ChatMessage(msg);
	this.messages.push(chatMsg);

	if (this.messages().length > this.MAX_MESSAGES) {
		this.messages.shift();
	}

	if (scrollBottom) {
		this.scrollToBottom();
	} else {
		this.hasUnreadMessages(true);
	}

	// Publish the message.
	this._pubsub.publish(Bestia.Signal.CHAT_RECEIVED, chatMsg);
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
 *            COMMAND, BATTLE.
 */
Bestia.Chat.prototype.addLocalMessage = function(msg, mode) {
	mode = mode || "SYSTEM";
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
