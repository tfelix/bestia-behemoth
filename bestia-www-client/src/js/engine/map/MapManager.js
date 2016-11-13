import MID from '../../io/messages/MID';
import Message from '../../io/messages/Message';
import WorldHelper from './WorldHelper';

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
	
	constructor(pubsub, renderFn) {
		if(!pubsub) {
			throw 'Pubsub can not be null.';
		}
		if(typeof renderFn !== 'function') {
			throw 'Callback to the renderer must be given.';
		}
		
		this._pubsub = pubsub;
		
		/**
		 * Callback which is invoked if there is the need to render a new chunk.
		 */
		this._renderFn = renderFn;
		
		this._chunkCache = {};
		/**
		 * Player pos in tile space.
		 */
		this._playerPos = {x: 0, y: 0};
		
		/**
		 * Viewport in tile space.
		 */
		this._viewPort = {x1: 0, y1: 0, x2: 0, y2: 0};
		
		/**
		 * This where the last chunk boundaries send to the renderer.
		 */
		this._lastChunkRender = {x1: 0, y1: 0, x2: 0, y2: 0};
		
		pubsub.subscribe(MID.MAP_CHUNK, this._handleChunkReceived.bind(this));
	}
	
	/**
	 * Handle if a new mapchunk is send by the server. It will get incorporated
	 * into the database.
	 */
	_handleChunkReceived() {
		// See if
	}
	
	/**
	 * Verifies that all chunks
	 */
	_checkChunkCache() {
		// Get the border chunks cords.
		let topLeft = {x: Math.trunc(this._viewPort.x1 / WorldHelper.CHUNK_SIZE), 
				y: Math.trunc(this._viewPort.y1 / WorldHelper.CHUNK_SIZE)};
		let bottomRight = {x: Math.trunc(this._viewPort.x2 / WorldHelper.CHUNK_SIZE),
				y: Math.trunc(this._viewPort.y2 / WorldHelper.CHUNK_SIZE)};
		
	}
	
	/**
	 * Transforms tile to chunk coordinates.
	 */
	_tileToGlobChunk(x ,y) {
		return {x: Math.trunc(x / WorldHelper.CHUNK_SIZE),
			y: Math.trunc(y / WorldHelper.CHUNK_SIZE)};
	}
	
	/**
	 * Gives the coordiantes of the tile inside the chunk itself.
	 */
	_tileToLocChunk(x, y) {
		return {x: x % WorldHelper.CHUNK_SIZE, y: y % WorldHelper.CHUNK_SIZE};
	}
	
	/**
	 * Key to identify the chunk inside the cache.
	 */
	_chunkKey(glob) {
		return glob.x + '_' + glob.y;
	}
	
	/**
	 * Removes all map chunks from the cache which are currently not visible
	 * anymore.
	 */
	clear() {
		// TODO Das implementieren.
	}
	
	/**
	 * Sets the player position in global space. This is important to keep this
	 * updated. Because of this position the MapManager will calculate the new
	 * vision boundarys and request new map data from the server.
	 */
	setPlayerPosition(x, y) {
		this._playerPos.x = x;
		this._playerPos.y = y;
		
		// Calculate the new viewport.
		this._viewPort.x1 = x - WorldHelper.SIGHT_RANGE_X;
		this._viewPort.y1 = y - WorldHelper.SIGHT_RANGE_Y;
		this._viewPort.x2 = x + WorldHelper.SIGHT_RANGE_X;
		this._viewPort.y2 = y + WorldHelper.SIGHT_RANGE_Y;
		
		this._checkChunkCache();
	}
	
	/**
	 * Returns the gid of a given tile coordinate from the cache. NULL is
	 * returned if the position is currently not cached. Best is to call this
	 * method only from the render callback so its ensured that currently all
	 * tiles are loaded an accessable.
	 */
	getTileGid(x, y) {
		let glob = this._tileToGlobChunk(x, y);
		let loc = this._tileToLocChunk(x, y);
		let ident = this._chunkKey(glob);
		
		if(!this._checkChunkCache.hasOwnProperty(ident)) {
			return null;
		}
		
		let pos = loc.y * WorldHelper.CHUNK_SIZE + loc.x;
		return this._checkChunkCache[ident].gids[pos];
	}
	
}