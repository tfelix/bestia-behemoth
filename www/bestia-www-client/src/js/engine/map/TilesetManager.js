import MID from '../../io/messages/MID';
import Message from '../../io/messages/Message';
import LOG from '../../util/Log';

const TILE_KEY_PREFIX = 'tiles_';

/**
 * The tileset manager can be queried about the the tile data for a given gid
 * tile. If the tileset containing this gid tile data is already cached the manager will 
 * return the tileset information it has.
 * <p>
 * Otherwise the caller can request to obtain the tileset and give a callback to
 * get noticed as soon as the data has arrived.
 * </p>
 * <p>
 * Currently the internal lookup is very simple with a array. It might be needed
 * (especially if there are many many more tilesets to come) to implement a
 * better lookup strategy like maybe a tree.
 * </p>
 */
export default class TilesetManager {

	/**
	 * Ctor.
	 * 
	 * @param Pubsub -
	 *            Pubsub interface.
	 */
	constructor(ctx) {

		this._pubsub = ctx.pubsub;

		this._tilestes = [];
		this._callbacks = {};

		this._ctx = ctx;

		// map.tileset
		this._pubsub.subscribe(MID.MAP_TILESET, this._handleTilesetReceived.bind(this));
	}

	/**
	 * Inserts the new tileset into the right position.
	 */
	_handleTilesetReceived(_, data) {

		let key = TILE_KEY_PREFIX + data.ts.name;
		// Save the generated key so we can use it later.
		data.ts.key = key;

		// Do we have the image already saved?
		if (this._ctx.loader.has(key, 'image')) {
			this._handleTilesetCompleteLoad(data.ts);
		} else {
			// Prepare the loader and set a callback.
			this._ctx.loader.load({
				key: key,
				type: 'image',
				url: this._ctx.url.getTilemapUrl(data.ts.name)
			}, function () {
				this._handleTilesetCompleteLoad(data.ts);
			}.bind(this));
		}
	}

	/**
	 * Callback if the tileset image and data was completly loaded.
	 * 
	 * @param ts -
	 *            Contains the data description of a tileset.
	 */
	_handleTilesetCompleteLoad(ts) {
		let endGid = ts.maxgid;
		// Special case: if array is empty just add it.
		if (this._tilestes.length === 0) {
			this._tilestes.push(ts);
			this._runCallbacks(ts);
			return;
		}

		for (let i = 0; i < this._tilestes.length; i++) {
			let ts = this._tilestes[i];
			if (ts.maxgid <= endGid) {
				// Insert at the end.
				this._tilestes.splice(i, 0, ts);
				this._runCallbacks(ts);
				return;
			}
		}
	}

	/**
	 * Checks if there a callbacks saved for a given gid. Calls the callbacks
	 * and deliveres reference to this object and the originally requested gid.
	 */
	_runCallbacks(tileset) {
		// Search for callbacks waiting for this tileset.
		Object.keys(this._callbacks).forEach(function (gid) {
			if (tileset.mingid <= gid && tileset.maxgid >= gid) {
				let fns = this._callbacks[gid];
				delete this._callbacks[gid];
				fns.forEach(function (fn) {
					try {
						fn(gid, this);
					} catch(err) {
						LOG.error('TilesetManager#_runCallbacks: Error during tileset callback.' + err);
					}
				}, this);
			}
		}, this);
	}

	/**
	 * Checks if one or all gids are available right now inside the cache. The
	 * gids can be a single number or an array. All queried gids inside the
	 * array must be inside the cache in order to return a TRUE.
	 * 
	 * @param Number
	 *            gids - The gid of the tiles to check inside this cache.
	 */
	hasTileset(gid) {
		for (let i = 0; i < this._tilestes.length; i++) {
			if (gid <= this._tilestes[i].maxgid && gid >= this._tilestes[i].mingid) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns all cached tilesets.
	 */
	getCachedTilesets() {
		return this._tilestes;
	}

	/**
	 * Requests a tileset from the server. It will fire the callback as soon as
	 * the tileset is there or immediately if the tileset is already found
	 * inside this manager.
	 * 
	 * TODO Das muss smarter sein. Wenn der Server bereits nach einer GID
	 * gefragt wurde die nah zusammen liegt, dann muss man ein paar ms warten ob
	 * ggf eine Antwort kommt, bevor man erneut nachfragt.
	 */
	getTileset(gid, fn) {
		if (typeof fn !== 'function') {
			throw 'fn must be of type function';
		}

		// See if we can directly deliver or if we must ask the server first.
		for (let i = 0; i < this._tilestes.length; i++) {
			if (gid <= this._tilestes[i].maxgid && gid >= this._tilestes[i].mingid) {
				fn(this._tilestes[i], this);
				return;
			}
		}

		this._requestTileset(gid, fn);
	}

	/**
	 * Whipes the whole tileset cache.
	 */
	clear() {
		this._tilestes = [];
	}

	/**
	 * Requests the tileset from the server. Will return as soon as the tilset
	 * gets deliverd.
	 */
	_requestTileset(gid, fn) {

		if (!this._callbacks[gid]) {
			this._callbacks[gid] = [fn];
		} else {
			this._callbacks[gid].push(fn);
		}

		this._pubsub.send(new Message.MapTilesetRequest(gid));
	}
}