import Renderer from './Renderer';
import WorldHelper from '../map/WorldHelper';
import MID from '../../io/messages/MID';
import Signal from '../../io/Signal';
import Message from '../../io/messages/Message';
import TilesetManager from '../map/TilesetManager';
import NOOP from '../../util/NOOP';
import { pathfinder } from '../EngineData';
import LOG from '../../util/Log';

const MIN_SAFETY_TILES = 3;
const NAME = 'tile';

/**
 * The tile renderer is responsible for controlling and managing the correct
 * rendering of tiles. To perform its duty it needs to tap into the map manager
 * and the tileset manager as well.
 */
export default class TileRender extends Renderer {

	constructor(pubsub, game) {
		super();

		this._pubsub = pubsub;
		this._game = game;
		this._isFirstDraw = true;
		this._lastPlayerPos = { x: -1, y: -1 };

		// Some basic init.
		this.clear();

		this._pubsub.subscribe(MID.MAP_CHUNK, this._handleChunkReceived, this);

		this._pubsub.subscribe(Signal.ENGINE_GAME_STARTED, function () {
			this.clearDraw();
		}, this);

		this._pubsub.subscribe(Signal.BESTIA_SELECTED, function (_, bestia) {
			this._isFirstDraw = true;
			this._playerBestia = bestia;
		}, this);
	}

	/**
	 * The name of this render.
	 */
	get name() {
		return NAME;
	}

	/**
	 * Checks if we need a redraw.
	 */
	isDirty() {

		if (this._isFirstDraw) {
			return true;
		}

		return this._lastPlayerPos.x !== this._playerBestia.posX()
			|| this._lastPlayerPos.y !== this._playerBestia.posY();
	}

	/**
	 * Clears all chunks and basically resets the tile renderer.
	 */
	clear() {
		this._tilesetManager = new TilesetManager();
		this._rendered = { x1: 0, x2: 0, y1: 0, y2: 0 };
		this._newRendered = { x1: 0, x2: 0, y1: 0, y2: 0 };
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
		this._gameSize = { x: 0, y: 0 };
	}

	/**
	 * The chunks (array) with the given id are loaded. If a callback is given
	 * the callback is fired when all chunks AND their corresponding tile
	 * information was acquired from the server.
	 */
	loadChunks(chunks, fn) {
		if (!(chunks instanceof Array)) {
			chunks = [chunks];
		}

		fn = fn || NOOP;

		// We generate the job key which is later used to identify the
		// loading process.
		let key = chunks.length + '-' + chunks[0].x + '-' + chunks[0].y;

		// Does the same key exist? if so abort.
		if (this._chunkCallbackCache.hasOwnProperty(key)) {
			this._chunkCallbackCache[key].fn.push(fn);
		}

		this._chunkCallbackCache[key] = fn;
		this._pubsub.send(new Message.MapChunkRequest(chunks));
	}

	/**
	 * Gives a key which can be used to reference a chunk inside the cache.
	 */
	_chunkKey(x, y) {
		return 'x' + x + '-y' + y;
	}

	/**
	 * Returns the GID of the tile under this x and y position of the map. If
	 * the tile/chunk was not yet loaded null is returned.
	 */
	_getGid(x, y) {
		let glob = this._tileToGlobChunk(x, y);
		let loc = this._tileToLocChunk(x, y);
		let key = this._chunkKey(glob.x, glob.y);

		if (!this._chunkCache.hasOwnProperty(key)) {
			return null;
		}

		return this._chunkCache[key].gl[loc.y * WorldHelper.CHUNK_SIZE + loc.x];
	}

	/**
	 * Handle if a new mapchunk is send by the server. It will get incorporated
	 * into the cache.
	 */
	_handleChunkReceived(_, data) {
		// Generate the job key.
		let key = data.c.length + '-' + data.c[0].p.x + '-' + data.c[0].p.y;

		// Check the callback.
		let callbackFn = this._chunkCallbackCache[key];
		delete this._chunkCallbackCache[key];

		// Iterate over all tiles inside the chunk message and group the tile
		// ids.
		let tileGids = new Set();
		for (let c = 0; c < data.c.length; c++) {
			let chunk = data.c[c];

			let chunkKey = this._chunkKey(chunk.p.x, chunk.p.y);
			this._chunkCache[chunkKey] = chunk;

			for (let x = 0; x < WorldHelper.CHUNK_SIZE; x++) {
				for (let y = 0; y < WorldHelper.CHUNK_SIZE; y++) {

					let gid = chunk.gl[y * WorldHelper.CHUNK_SIZE + x];
					tileGids.add(gid);

				}
			}
		}

		let tilesToLoad = tileGids.size;
		let tileCallback = function () {

			tilesToLoad--;

			if (tilesToLoad === 0) {
				callbackFn();
			}
		}.bind(this);

		tileGids.forEach(function (gid) {
			this._tilesetManager.getTileset(gid, tileCallback);
		}, this);
	}

	/**
	 * This function returns an array with chunk cords which are visible right
	 * now depending on the game size and player position.
	 */
	getVisibleChunks() {
		let xChunks = Math.ceil(WorldHelper.SIGHT_RANGE.x / WorldHelper.CHUNK_SIZE);
		let yChunks = Math.ceil(WorldHelper.SIGHT_RANGE.y / WorldHelper.CHUNK_SIZE);
		let playerChunk = this._tileToGlobChunk(this._playerBestia.posX(), this._playerBestia.posY());
		let chunks = [];

		for (let x = playerChunk.x - xChunks; x < playerChunk.x + xChunks; x++) {
			for (let y = playerChunk.y - xChunks; y < playerChunk.y + yChunks; y++) {
				if (x >= 0 && y >= 0) {
					chunks.push({ x: x, y: y });
				}
			}
		}

		return chunks;
	}

	/**
	 * Transforms tile to chunk coordinates.
	 */
	_tileToGlobChunk(x, y) {
		return {
			x: Math.trunc(x / WorldHelper.CHUNK_SIZE),
			y: Math.trunc(y / WorldHelper.CHUNK_SIZE)
		};
	}

	/**
	 * Transforms chunk cords to tile cords.
	 */
	_chunkToTile(chunkX, chunkY) {
		return { x: chunkX * WorldHelper.CHUNK_SIZE, y: chunkY * WorldHelper.CHUNK_SIZE };
	}

	/**
	 * Gives the coordiantes of the tile inside the chunk itself.
	 */
	_tileToLocChunk(x, y) {
		return { x: x % WorldHelper.CHUNK_SIZE, y: y % WorldHelper.CHUNK_SIZE };
	}

	/**
	 * Clears the whole screen and setup a complete new rendering from the
	 * current player position.
	 */
	clearDraw() {

		if (this._playerBestia == null) {
			LOG.error('PlayerBestia not found in context. Can not draw tilemap.');
			return;
		}

		// Prepare the new tilemap.
		this._map = this._game.add.tilemap();

		// We must create tileset images for every tileset image loaded.
		this._tilesetManager.getCachedTilesets().forEach(function (ts) {
			this._map.addTilesetImage(ts.key, null, 32, 32, 0, 0, ts.mingid);
		}, this);

		this._layer = this._map.create('ground', 90, 90, 32, 32);

		this._layer.resizeWorld();
		this._layer.sendToBack();

		// We must calculate the game size.
		this._gameSize = WorldHelper.getTileXY(this._game.width, this._game.height);

		let pos = { x: this._playerBestia.posX(), y: this._playerBestia.posY() };

		let startX = Math.max(0, pos.x - WorldHelper.SIGHT_RANGE.x);
		let startY = Math.max(0, pos.x - WorldHelper.SIGHT_RANGE.y);
		let endX = pos.x + WorldHelper.SIGHT_RANGE.x;
		let endY = pos.y + WorldHelper.SIGHT_RANGE.y;


		for (var x = startX; x < endX; x++) {
			for (var y = startY; y < endY; y++) {

				let gid = this._getGid(x, y);
				this._map.putTile(gid, x, y, 'ground');
			}
		}

		this._rendered = { x1: startX, x2: endX, y1: startY, y2: endY };
		this._lastPlayerPos.x = this._playerBestia.posX();
		this._lastPlayerPos.y = this._playerBestia.posY();

		this._updatePathInfo();
	}

	/**
	 * Updates the path finder information with the new data.
	 */
	_updatePathInfo() {
		// Set the grid to a static value. Must be updated when player moves.
		var grid = new Array(this._gameSize.y);

		for (var y = 0; y < this._gameSize.y; y++) {

			grid[y] = new Array(this._gameSize.x);

			for (var x = 0; x < this._gameSize.x; x++) {

				var offsetX = this._rendered.x1 + x;
				var offsetY = this._rendered.y1 + y;

				//var gid = this._getGid(offsetX, offsetY);

				// TODO Feststellen ob die GID lauffähig ist oder nicht.
				var isGidWalkable = true;
				if (isGidWalkable) {
					grid[y][x] = 1;
				} else {
					grid[y][x] = 0;
				}
			}
		}

		var offset = { x: this._rendered.x1, y: this._rendered.y1 };

		LOG.debug('Updating the pathfinder data with offset: ', offset);

		pathfinder.setGrid(offset, grid);
		pathfinder.setAcceptableTiles([1]);
	}

	/**
	 * Recalculates the current distances from the player to the border and
	 * decides if a re-render of the map is needed.
	 */
	update() {

		if (!this._playerBestia) {
			return;
		}

		if (this._isFirstDraw) {
			this._isFirstDraw = false;
			this.clearDraw();
		}

		let tPos = { x: this._playerBestia.posX(), y: this._playerBestia.posY() };

		// Tile distance to display left.
		let tdLeft = Math.max(0, tPos.x - this._rendered.x1);
		let tdRight = this._rendered.x2 - tPos.x;
		let tdTop = Math.max(0, tPos.y - this._rendered.y1);
		let tdBottom = this._rendered.y2 - tPos.y;

		// Check if we need an extension at the right.
		if (tdRight <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 + WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 + WorldHelper.CHUNK_SIZE;

			// Add the new tiles.
			for (let x = this._rendered.x2; x < newX2; x++) {
				for (let y = this._rendered.y1; y < this._rendered.y2; y++) {
					let gid = this._getGid(x, y);
					this._map.putTile(1, x, y, this._layer);
				}
			}

			// Remove the old tiles.
			for (let x = this._rendered.x1; x < newX1; x++) {
				for (let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);
				}
			}

			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
			this._updatePathInfo();
		}

		// Check if we need an extension at the bottom.
		if (tdBottom <= MIN_SAFETY_TILES) {
			let newY2 = this._rendered.y2 + WorldHelper.CHUNK_SIZE;
			let newY1 = this._rendered.y1 + WorldHelper.CHUNK_SIZE;

			// Add the new tiles.
			for (let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for (let y = this._rendered.y1; y < newY2; y++) {
					this._map.putTile(30, x, y, this._layer);
				}
			}

			// Remove the old tiles.
			for (let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for (let y = this._rendered.y1; y < newY1; y++) {
					this._map.removeTile(x, y, this._layer);
				}
			}

			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
			this._updatePathInfo();
		}

		// Check if we need an extension at the left.
		if (tdLeft <= MIN_SAFETY_TILES) {
			let newX2 = this._rendered.x2 - WorldHelper.CHUNK_SIZE;
			let newX1 = this._rendered.x1 - WorldHelper.CHUNK_SIZE;

			// Add the new tiles.
			for (let x = newX1; x < this._rendered.x1; x++) {
				for (let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.putTile(30, x, y, this._layer);
				}
			}

			// Remove the old tiles at the right.
			for (let x = newX2; x < this._rendered.x2; x++) {
				for (let y = this._rendered.y1; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);
				}
			}

			this._rendered.x1 = newX1;
			this._rendered.x2 = newX2;
			this._updatePathInfo();
		}

		// Check if we need an extension at the top.
		if (tdTop <= MIN_SAFETY_TILES) {
			let newY1 = this._rendered.y1 - WorldHelper.CHUNK_SIZE;
			let newY2 = this._rendered.y2 - WorldHelper.CHUNK_SIZE;

			// Add the new tiles.
			for (let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for (let y = newY1; y < this._rendered.y1; y++) {
					this._map.putTile(30, x, y, this._layer);
				}
			}

			// Remove the old tiles.
			for (let x = this._rendered.x1; x < this._rendered.x2; x++) {
				for (let y = newY2; y < this._rendered.y2; y++) {
					this._map.removeTile(x, y, this._layer);
				}
			}

			this._rendered.y1 = newY1;
			this._rendered.y2 = newY2;
			this._updatePathInfo();
		}
	}
}
