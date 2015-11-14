/**
 * This controller will listen to incoming chat messages. If one public message
 * is detected it will spawn an chat message to be displayed. It basically
 * controls all the spawning, timing of public chat messages.
 */
Bestia.Engine.ChatEntityController = function(pubsub, game) {

	this._pubsub = pubsub;
	
	this._game = game;
	
	/**
	 * Chat message handler.
	 */
	var onChatMsgHandler = function(_, data) {
		
	};
	pubsub.subscribe(Bestia.Signal.CHAT_RECEIVED, onChatMsgHandler);
	
};

