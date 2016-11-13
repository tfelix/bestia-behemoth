import MID from '../../io/messages/MID';
import Message from '../../io/messages/Message';

/**
 * The map manager will store and retrieve map chunks by itself when the
 * position of the current entity is given to him. He calculates the field of
 * view and requests the apropriate chunks based on whats inside his cache or
 * not.
 * <p>
 * The load of the chunks can be controlled by callbacks. The callbacks will
 * only fire if the engines load has also loaded all appropriate graphics file
 * (and thus the engine can start to render this tiles).
 * </p>
 */
export default class MapManager {
	
	constructor(pubsub) {
		if(!pubsub) {
			throw 'Pubsub can not be null.';
		}
		
		this._pubsub = pubsub;
		
		pubsub.subscribe(MID.MAP_CHUNK, this._handleChunkReceived.bind(this));
	}
	
	/**
	 * Handle if a new mapchunk is send by the server. It will get incorporated
	 * into the database.
	 */
	_handleChunkReceived() {
		
	}
	
	
	getTile(x, y) {
		
	}
	
}