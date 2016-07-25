import Signal from '../../io/Signal.js';
import ChatTextEntity from '../entities/ChatTextEntity.js';

/**
 * This controller will listen to incoming chat messages. If one public message
 * is detected it will spawn an chat message to be displayed. It basically
 * controls all the spawning, timing of public chat messages.
 * 
 * @param {Bestia.Engine.EntityCacheManager}
 *            cache
 */
export default class ChatFx {
	constructor(ctx) {

		this._pubsub = ctx.pubsub;
	
		this._game = ctx.game;
	
		this._cache = ctx.entityCache;
	
		this._pubsub.subscribe(Signal.CHAT_RECEIVED, this._onChatMsgHandler.bind(this));
	}
	
	/**
	 * Chat message handler.
	 */
	_onChatMsgHandler(_, data) {
		if (data.mode() !== 'PUBLIC') {
			return;
		}

		// TODO Hier ein Caching implementieren. Das vielleicht als fx_manager
		// Service handhaben.

		var entity = this._cache.getByPlayerBestiaId(data.senderPlayerBestiaId());

		if (entity !== null) {
			var textEntity = new ChatTextEntity(this._game, data.text(), entity);
			textEntity.appear();
		}
	}

	destroy() {
		this._pubsub.unsubscribe(Signal.CHAT_RECEIVED, this._onChatMsgHandler);
	}
}