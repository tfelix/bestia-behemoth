/**
 * View Model for single messages.
 * 
 * @class Bestia.ChatMessage
 * @param {Object}
 *            msg - Message object to create the model.
 */
export default class ChatMessage {
	constructor(msg) {
		var self = this;
	
		/**
		 * Nickname of the user.
		 * 
		 * @public
		 * @property {string}
		 */
		self.nickname = ko.observable(msg.sn || '');
	
		/**
		 * Player bestia id of the sender of this message. Only if the message
		 * was a public message.
		 * 
		 * @public
		 * @property {Number}
		 */
		this.senderPlayerBestiaId = msg.pbid || 0;
		
		this.entityId = msg.eid || 0;
	
		/**
		 * Activated mode of the chat.
		 * 
		 * @public
		 * @property {string}
		 */
		self.mode = ko.observable(msg.m || '');
	
		/**
		 * Text of the chat message.
		 * 
		 * @public
		 * @property {string}
		 */
		self.text = ko.observable(msg.txt || '');
	
		/**
		 * Holds the CSS style name of the currently activated chatmode.
		 * 
		 * @public
		 * @property {string}
		 */
		self.cssMode = ko.pureComputed(function() {
			return self.mode().toLowerCase();
		});
	}
	
	static createLocalMessage(mode, text, whisperNick, localNick, pbid) {
		var msg = new ChatMessage();
		
		msg.text(text);
		msg.mode(mode);
		msg.nickname(localNick);
		msg.senderPlayerBestiaId(pbid);
		
		return msg;
	}
}
