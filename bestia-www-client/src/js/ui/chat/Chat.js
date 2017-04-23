/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

import ko from 'knockout';
import Message from '../../io/messages/Message';
import Signal from '../../io/Signal';
import MID from '../../io/messages/MID';
import ChatMessage from './ChatMessage';
import BasicCommand from './commands/BasicCommand';
import DebugCommand from './commands/DebugCommand';
import ClearCommand from './commands/ClearCommand';
import HelpCommand from './commands/HelpCommand';
import ModeGuildCommand from './commands/ModeGuildCommand';
import ModePublicCommand from './commands/ModePublicCommand';
import ModeWhisperCommand from './commands/ModeWhisperCommand';
import ModePartyCommand from './commands/ModePartyCommand';

/**
 * Number of max messages until old messages are discarded.
 * 
 * @constant
 */
const MAX_MESSAGES = 50;

/**
 * Custom knockout binding for scrolling on dom elements.
 */
/*
ko.bindingHandlers.scroll = {
	init: function (element, valueAccessor, allBindings, viewModel, bindingContext) {
		// This will be called when the binding is first applied to an element
		// Set up any initial state, event handlers, etc. here
		element.addEventListener('scroll', function () {
			if (element.scrollTop !== element.scrollHeight) {
				valueAccessor(false);
			}
		});
	},

	update: function (element, valueAccessor, allBindings, viewModel, bindingContext) {
		// This will be called once when the binding is first applied to an element,
		// and again whenever any observables/computeds that are accessed change
		// Update the DOM element based on the supplied values here.
		if (valueAccessor() === true) {
			element.scrollTop = element.scrollHeight;
		}
	}
};*/

/**
 * Chat for the bestia client. It subscribes to the necessairy messages to get
 * informed if new messages arrive. Updates and displays the messages. Will
 * provide chat functionality to the user.
 * 
 * @class Bestia.Chat
 */
export default class Chat {

	constructor(pubsub) {
		if (!pubsub) {
			throw 'Pubsub can not be null.';
		}

		this._pubsub = pubsub;

		/**
		 * Name of the bestia master. Is extracted from the game config which is
		 * set during login process.
		 * 
		 * @property
		 * @constant
		 */
		this._nickname = '';

		this._currentBestiaId = 0;
		this._currentEntityId = 0;


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

		/**
		 * Commands which will be checked at each keystroke if the must be
		 * invoked.
		 * 
		 * @private
		 */
		this._localRealtimeCommands = [];

		/**
		 * Current chat mode. Possible values are PUBLIC, PARTY or GUILD.
		 * 
		 * @public
		 * @property {String}
		 */
		this.mode = ko.observable('PUBLIC');

		/**
		 * Flag if the speech recognition is enabled for the chat.
		 * 
		 * @public
		 * @property {bool}
		 */
		this.speechEnabled = ko.observable(false);

		/**
		 * The translated text for the chat mode. Depends upon mode.
		 * 
		 * @public
		 * @property {String}
		 */
		this.modeText = ko.observable('Public');
		/*
		this.modeText = ko.computed(function() {
			if (this.mode() == 'PUBLIC') {
				return this._i18n.t('chat.public');
			}
			if (this.mode() == 'PARTY') {
				return this._i18n.t('chat.party');
			}
			if (this.mode() == 'GUILD') {
				return this._i18n.t('chat.guild');
			}
	
			return this._i18n.t('chat.public');
		});*/

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
		 * If set to true the chat always scrolls to the bottom when a new message arrives.
		 * 
		 * @public
		 * @property {boolean}
		 */
		this.scrollBottom = ko.observable(true);

		/**
		 * Holds the text of the chat.
		 */
		this.text = ko.observable('');

		// Check for constant updates to this value e.g. if the user is typing
		// to this property. react to certain inputs on the fly.
		this.text.subscribe(this._identifyLocalCommandTyping.bind(this));

		// Finally subscribe to chat messages.
		this._pubsub.subscribe(MID.CHAT_MESSAGE, this.addMessage, this);

		// Catch authentication to set username for the chat. We can remove
		// ourself
		// once this is done.
		var handleAuthEvent = function (_, data) {
			this._nickname = data.username;
			this._pubsub.unsubscribe(Signal.IO_AUTH_CONNECTED, handleAuthEvent);
		};
		this._pubsub.subscribe(Signal.IO_AUTH_CONNECTED, handleAuthEvent, this);

		// Handle the selection of a new bestia for the bestia id
		// (chat messages are input messages).
		this._pubsub.subscribe(Signal.BESTIA_SELECTED, function (_, bestia) {
			this._currentBestiaId = bestia.playerBestiaId();
			this._currentEntityId = bestia.entityId();
		}, this);

		// When the game enters the loading menu hide the chat.
		this._pubsub.subscribe(Signal.ENGINE_PREPARE_MAPLOAD, function () {
			this.isVisible(false);
		}, this);

		// When the game is displayed also display the chat.
		this._pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, function () {
			this.isVisible(true);
		}, this);

		this._pubsub.subscribe(Signal.INVENTORY_ITEM_ADD, this._handleItemObtainedMsg, this);
		this._pubsub.subscribe(Signal.CHAT_REGISTER_CMD, this._handleRegisterCommand, this);
		this._pubsub.subscribe(Signal.IO_DISCONNECTED, this.clear, this);

		// Register the chat specific local commands.
		this._handleRegisterCommand(null, new ClearCommand());
		this._handleRegisterCommand(null, new HelpCommand());
		this._handleRegisterCommand(null, new DebugCommand());

		// This commands a checked right at the typing of the user.
		this._localRealtimeCommands.push(new ModePublicCommand());
		this._localRealtimeCommands.push(new ModePartyCommand());
		this._localRealtimeCommands.push(new ModeGuildCommand());
		this._localRealtimeCommands.push(new ModeWhisperCommand());

		// Some special checks.
		if (window.hasOwnProperty('webkitSpeechRecognition')) {
			this.speechEnabled(true);
		}
	}

	/**
	 * Starts the speech recognition for the chat system.
	 */
	recognizeSpeech() {
		if (!this.speechEnabled()) {
			return;
		}

		if (!this._recognition) {
			this._recognition = new webkitSpeechRecognition();
			this._recognition.continuous = false;
			this._recognition.interimResults = false;
			// TODO Sprache muss dynamisch mit der sprache angepasst werden.
			this._recognition.lang = 'de-DE';

			this._recognition.onresult = function (e) {
				this.text(e.results[0][0].transcript);
				this._recognition.stop();
				this.sendChat();
			}.bind(this);

			this._recognition.onerror = function () {
				this._recognition.stop();
			};
		}

		this._recognition.start();
	}

	/**
	 * Triggers if a chat is about to be send. Create a chat message of all the
	 * typed information and send it to the server.
	 * 
	 * @public
	 * @method Bestia.Chat#sendChat
	 */
	sendChat() {
		var msgText = this.text();

		if (msgText.length === 0) {
			return;
		}

		// Clear text.
		this.text('');

		var localCmd = this._localCommands;
		for (var i = 0; i < localCmd.length; i++) {
			if (localCmd[i].executeCommand(msgText, this, this._pubsub) === true) {
				// Command was handled.
				return;
			}
		}

		// Prepare and send the message to the server and add it to the local
		// chat.
		var msg = new Message.Chat(this.mode(), msgText, this.whisperNick(), this._nickname,
			this._currentBestiaId);

		// Check if this was a command to be executed on the server and set the
		// message flag accordingly.
		if (msgText.slice(0, 1) === '/') {
			msg.m = 'COMMAND';
		} else {
			// Attach additional data to the message for the server.
			msg.pbid = this._currentBestiaId;
			msg.eid = this._currentEntityId;
		}

		this._pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
	}

	/**
	 * Clears all chat input.
	 */
	clear() {
		this.messages.removeAll();
	}

	/**
	 * Changes the mode of the model. Is used to bind against as a view
	 * callback.
	 * 
	 * @public
	 * @method Bestia.Chat#changeMode
	 */
	changeMode(mode) {
		this.mode(mode);
	}

	/**
	 * Adds a message to the chat model. Message is from the server and
	 * therefore a server object. If scrolled to bottom stays at bottom. If not
	 * display a notification that there are new messages waiting.
	 * 
	 * @method Bestia.Chat#addMessage
	 * @param {object}
	 *            msg - Chat message from the server.
	 * @public
	 */
	addMessage(_, msg) {

		// Swap the messages if the method was called directly and not via a pubsub call (no topic given then)
		if (msg === undefined) {
			msg = _;
		}

		var chatMsg = new ChatMessage(msg);
		this.messages.push(chatMsg);

		if (this.messages().length > MAX_MESSAGES) {
			this.messages.shift();
		}

		// Publish the message.
		this._pubsub.publish(Signal.CHAT_RECEIVED, chatMsg);
	}

	/**
	 * Disables the game controls if the chat is focused.
	 */
	disableGameControls() {
		this._pubsub.publish(Signal.INPUT_LISTEN, false);
	}

	/**
	 * Enables the game controls again.
	 */
	enableGameControls() {
		this._pubsub.publish(Signal.INPUT_LISTEN, true);
	}

	/**
	 * Translates the message when an item was added to the inventory and
	 * displays it in the chat.
	 * 
	 * @param _
	 * @param item
	 */
	_handleItemObtainedMsg(_, item, newAmount) {

		var self = this;

		this._i18n.t('chat.item_obtained', function (t) {
			var text = t('chat.item_obtained').format(item.name(), newAmount);
			self.addLocalMessage(text);
		});

	}

	/**
	 * Registers an incoming command into the chat system so it can be used as a
	 * local command.
	 * 
	 * @param _
	 *            {String} Topic.
	 * @param cmd
	 *            {Bestia.Chat.Commands.BasicCommand} The new command to be
	 *            registered.
	 */
	_handleRegisterCommand(_, cmd) {
		if (!(cmd instanceof BasicCommand)) {
			console.warn('Chat#_handleRegisterCommand: Command is not an instance of BasicCommand.');
			console.warn(cmd);
			return;
		}

		console.debug('Registering Chat command: ' + cmd.cmdHandle);

		this._localCommands.push(cmd);
	}

	/**
	 * Scrolls to the latest messages in the chat and sets the flag
	 * {@code hasUnreadMessages} to false.
	 * 
	 * @method Bestia.Chat#scrollToBottom
	 * @public
	 */
	scrollToBottom() {
		this.hasUnreadMessages(false);
	}

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
	addLocalMessage(msg, mode) {
		mode = mode || 'SYSTEM';
		var msgObj = new Message.Chat(mode, msg);
		this.addMessage(msgObj);
	}

	/**
	 * Identifying local chat commands which can be executed directly by the
	 * client. Later when this gets more complex we might need to refactor this
	 * as an extra class.
	 */
	_identifyLocalCommandTyping(str) {

		this._localRealtimeCommands.forEach(function (val) {
			val.isCommand(str, this);
		}, this);

	}
}