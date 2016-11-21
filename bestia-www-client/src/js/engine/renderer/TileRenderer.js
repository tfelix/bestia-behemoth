import WorldHelper from '../map/WorldHelper';
import MID from '../../io/messages/MID';
import Message from '../../io/messages/Message';
import TilesetManager from '../map/TilesetManager';
import NOOP from '../../util/NOOP';

const MIN_SAFETY_TILES = 3;

/**
 * The tile renderer is responsible for controlling and managing the correct
 * rendering of tiles. To perform its duty it needs to tap into the map manager
 * and the tileset manager as well.
 */
export default class TileRenderer {
	
	constructor(ctx) {
	
		this._ctx = ctx;
		this._game = ctx.game;
		this._tilesetManager = new TilesetManager(ctx.pubsub, ctx.loader, ctx.url);
		
		this._sprite = null;
		
		this._rendered = {x1: 0, x2: 0, y1: 0, y2: 0};
		this._newRendered = {x1: 0, x2: 0, y1: 0, y2: 0};
		
		this._map = this._game.add.tilemap();
		this._map.addTilesetImage('tilemap');
		this._layer = this._map.create('ground', 90, 60, 32, 32);
		this._layer.resizeWorld();
		this._layer.sendToBack();
		
		this._gameSize = {x: 0, y: 0};
		
		/**
		 * The chunks from the server are called sequentially. If all chunks
		 * have been received we ask for its tile data. {x: x, y: y,
		 * tilesToLoad: 0, fn}
		 */
		this._chunkCallbackCache = {};
		
		/**
		 * Cache object for loaded chunks.
		 */
		this._chunkCache = {};
		
		ctx.pubsub.subscribe(MID.MAP_CHUNK, this._handleChunkReceived.bind(this));
	}
	
	/**
	 * The chunks with the given id a loaded. If a callback is given the
	 * callback is fired when all chunks and their corresponding tile
	 * information was acquired from the server.
	 */
	loadChunks(chunk, fn) {
		let key = this._chunkKey(chunk.x, chunk.y);
		fn = fn || NOOP;
		
		// Does the same key exist? if so abort.
		if(this._chunkCallbackCache.hasOwnProperty(key)) {
			this._chunkCallbackCache[key].fn.push(fn);
		}
		
		this._chunkCallbackCache[key] = {fn: [fn]};
		this._ctx.pubsub.send(new Message.MapChunkRequest(chunk.x, chunk.y));
	}
	
	/**
	 * Gives a key which can be used to reference a chunk inside the cache.
	 */
	_chunkKey(x, y) {
		return "x"+x+"-y"+y;
	}
	
	/**
	 * Handle if a new mapchunk is send by the server. It will get incorporated
	 * into the database.
	 */
	_handleChunkReceived(_, data) {
		let key = this._chunkKey(data.p.x, data.p.y);
		
		// Check the callback.
		let chunkCallback = this._chunkCallbackCache[key];
		chunkCallback.tilesToLoad = WorldHelper.CHUNK_SIZE * WorldHelper.CHUNK_SIZE;
		
		// Iterate over tile inside chunk cords.
		let tileCords = this._chunkToTile(data.p.x, data.p.y);
		for(let x = tileCords.x; x < tileCords.x + WorldHelper.CHUNK_SIZE; x++) {
			for(let y = tileCords.y; y < tileCords.y + WorldHelper.CHUNK_SIZE; y++) {
				
				let gid = data.gl[y *  WorldHelper.CHUNK_SIZE + x];
				
				// Load all the tilesets associated with the given gids.
				
				// TODO This can be handled far more efficently. Maybe group the
				// ids before the request.
				this._tilesetManager.getTileset(gid, function(){
					
					chunkCallback.tilesToLoad--;
					
					if(chunkCallback.tilesToLoad === 0) {	
						
						delete this._chunkCallbackCache[key];
						this._chunkCache[key] = data;
						if(chunkCallback.fn !== undefined) {
							chunkCallback.fn.forEach(function(x){
								x();
							}, this);
						}
					}			
				}.bind(this));
			}
		}
	}
	
	/**
	 * This function returns an array with chunk cords which are visible right
	 * now depending on the game size and player position.
	 */
	getVisibleChunks() {
		let pb = this._ctx.playerBestia;
		let xChunks = Math.ceil(WorldHelper.SIGHT_RANGE.x / WorldHelper.CHUNK_SIZE);
		let yChunks = Math.ceil(WorldHelper.SIGHT_RANGE.y / WorldHelper.CHUNK_SIZE);
		let playerChunk = this._tileToGlobChunk(pb.posX(), pb.posY());
		let chunks = [];
		
		for(let x = playerChunk.x - xChunks; x < playerChunk.x + xChunks; x++) {
			for(let y = playerChunk.y - xChunks; y < playerChunk.y + yChunks; y++) {
				if(x >= 0 && y >= 0) {
					chunks.push({x: x, y: y});
				}
			}
		}
		
		return chunks;
	}
	
	/**
	 * Transforms tile to chunk coordinates.
	 */
	_tileToGlobChunk(x ,y) {
		return {x: Math.trunc(x / WorldHelper.CHUNK_SIZE),
			y: Math.trunc(y / WorldHelper.CHUNK_SIZE)};
	}
	
	/**
	 * Transforms chunk cords to tile cords.
	 */
	_chunkToTile(chunkX ,chunkY) {
		return {x: chunkX * WorldHelper.CHUNK_SIZE, y: chunkY * WorldHelper.CHUNK_SIZE};
	}
	
	/**
	 * Gives the coordiantes of the tile inside the chunk itself.
	 */
	_tileToLocChunk(x, y) {
		return {x: x % WorldHelper.CHUNK_SIZE, y: y % WorldHelper.CHUNK_SIZE};
	}
	
	/**
	 * Sets the current player sprite.
	 */
	set playerSprite(sprite) {
		this._sprite = sprite;
	}
	
	/**
	 * Clears the whole screen and setup a complete new rendering from the
	 * current player position.
	 */
	clearDraw() {
		// We must calculate the game size.
		this._gameSize.x = Math.ceil(this._game.width / WorldHelper.TILE_SIZE);
		this._gameSize.y = Math.ceil(this._game.height / WorldHelper.TILE_SIZE);
		
		
		let pos = WorldHelper.getTileXY(this._sprite.x, this._sprite.y);
		let startX = pos.x - WorldHelper.SIGHT_RANGE.x;
		let startY = pos.x - WorldHelper.SIGHT_RANGE.y;
		
		
		for(var x = startX; x < startX + this._gameSize.x; x++) {
			for(var y = startY; y < startY + this._gameSize.y; y++) {
				
				// Austauschen mit echten, tile informationen.
				if(x % 2 == 0) {
					this._map.putTile(30, x, y, 'ground');
				} else {
					this._map.putTile(54, x, y, 'ground');
				}
				
			}
		}
		
		this._rendered = {x1: startX, x2: startX + this._gameSize.x, y1: startY, y2: startY + this._gameSize.y};
	}
	
	/**
	 * Recalculates the current distances from the player to the border and
	 * decides if a re-render of the map is needed.
	 */
	update() {
		let tPos = WorldHelper.getTileXY(this._sprite.x, this._sprite.y);
		
		// Tile distance left.
		let tdLeft = tPos.x - this._rendered.x1;
		let tdRight = this._rendered.x2 - tPos.x;
		let tdTop = tPos.y - this._rendered.y1;
		let tdBottom = this._rendered.y2 - tPos.y;
		
		// Check if we need an extension at the right.
		if(tdRight <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 + WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 + WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x2; x < newX2; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < newX1; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
		}
		
		// Check if we need an extension at the bottom.
		if(tdBottom <= MIN_SAFETY_TILES) {
			let newY2 = this._rendered.y2 + WorldHelper.CHUNK_SIZE;
			let newY1 = this._rendered.y1 + WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < newY2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < newY1; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
		}

		// Check if we need an extension at the left.
		if(tdLeft <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 - WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 - WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = newX1; x < this._rendered.x1; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles at the right.
			for(let x = newX2; x < this._rendered.x2; x++) {
				for(let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
		}

		// Check if we need an extension at the top.
		if(tdTop <= MIN_SAFETY_TILES) {
			let newY1 = this._rendered.y1 - WorldHelper.CHUNK_SIZE;
			let newY2 = this._rendered.y2 - WorldHelper.CHUNK_SIZE;
			
			// Add the new tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = newY1; y < this._rendered.y1; y++) {
					this._map.putTile(30, x, y, this._layer);				
				}
			}
			
			// Remove the old tiles.
			for(let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for(let y = newY2; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);			
				}
			}
			
			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
		}
	}	
}