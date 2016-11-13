import MID from '../../io/messages/MID';
import Message from '../../io/messages/Message';

/**
 * The tileset manager can be queried about the the tile data for a given gid
 * tile. If the tileset is already cached the manager will return the tileset
 * information it has.
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
	
	constructor(pubsub) {
		if(!pubsub) {
			throw 'Pubsub can not be null.';
		}
		
		this._pubsub = pubsub;
		this._tilestes = [];
		this._callbacks = {};
		
		pubsub.subscribe(MID.MAP_TILESET, this._handleTilesetReceived.bind(this));
	}
	
	/**
	 * Inserts the new tileset into the right position.
	 */
	_handleTilesetReceived(_, data) {
		let endGid = data.ts.maxgid;	
		
		// Special case: if array is empty just add it.
		if(this._tilestes.lengtj === 0) {
			this._tilestes.push(data.ts);
			this._runCallbacks(data.ts);
			return;
		}
		
		for(let i = 0; i < this._tilestes.length; i++) {
			let ts = this._tilestes[i];
			if(ts.maxgid <= endGid) {
				// Insert at the end.
				this._tilestes.splice(i, 0, data.ts);
				this._runCallbacks(data.ts);
				return;
			}
		}
	}
	
	/**
	 * Checks if there a callbacks saved for a given gid. Calls the callbacks
	 * and deliveres reference to this object and the originally requested gid.
	 */
	_runCallbacks(tileset) {
		// Search for callbacks falling into this tileset.
		Object.keys(this._callbacks).forEach(function(gid) {
			if(tileset.mingid <= gid && tileset.maxgid >= gid) {
				let fns = this._callbacks[gid];
				delete this._callbacks[gid];
				fns.forEach(function(fn) {
					fn(gid, this);
				}, this);
			}
		});
	}
	
	/**
	 * Requests a tileset from the server. It will fire the callback as soon as
	 * the tileset is there or immediately if the tileset is already found
	 * inside this manager.
	 */
	getTileset(gid, fn) {
		if(typeof fn !== 'function') {
			throw 'fn must be of type function';
		}
		
		// See if we can directly deliver or if we must ask the server first.
		this._tilestes.forEach(function(ts) {
			if(gid <= ts.maxgid && gid >= ts.mingid) {
				fn(ts, this);
				return;
			}
		});
		
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
		
		if(!this._callbacks[gid]) {
			this._callbacks[gid] = [fn];
		} else {
			this._callbacks[gid].push(fn);
		}
		
		this._pubsub.send(new Message.MapTilesetRequest(gid));
	}
}