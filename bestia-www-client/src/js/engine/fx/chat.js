Bestia.Engine.FX = Bestia.Engine.FX || {};

/**
 * This controller will listen to incoming chat messages. If one public message
 * is detected it will spawn an chat message to be displayed. It basically
 * controls all the spawning, timing of public chat messages.
 * 
 * @param {Bestia.Engine.EntityCacheManager} -
 *            cache
 */
Bestia.Engine.FX.Chat = function(pubsub, cache, game) {

	this._pubsub = pubsub;

	this._game = game;

	this._cache = cache;

	this._pubsub.subscribe(Bestia.Signal.CHAT_RECEIVED, this._onChatMsgHandler.bind(this));
};

/**
 * Chat message handler.
 */
Bestia.Engine.ChatEntityController.prototype._onChatMsgHandler = function(_, data) {
	if (data.mode() !== 'PUBLIC') {
		return;
	}

	var entity = this._cache.getByPlayerBestiaId(data.senderPlayerBestiaId());

	if (entity !== null) {
		var textEntity = new Bestia.Engine.ChatTextEntity(this._game, data.text(), entity);
		textEntity.appear();
	}
};

Bestia.Engine.ChatEntityController.prototype.destroy = function() {
	this._pubsub.unsubscribe(Bestia.Signal.CHAT_RECEIVED, this._onChatMsgHandler);
};