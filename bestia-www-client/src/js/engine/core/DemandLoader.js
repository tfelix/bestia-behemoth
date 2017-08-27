import NOOP from '../../util/NOOP.js';
import LOG from '../../util/Log';

/**
 * Performs and manages on demand reloads for assets. If something has to be
 * loaded it can be handed over to this class. This will check via the phaser
 * Loader if the object already exists, if one can ask the on demand loader to
 * load it with a given callback function. After loading has completed the
 * function will be executed once.
 * 
 * @param {Phaser.Loader}
 *            loader - Reference to the phaser loader since we must hook into
 *            that one.
 */
export default class DemandLoader {
	
	constructor(ctx) {
		
		this._cache = {};
		
		this._ctx = ctx;
		
		this._phaserCache = ctx.game.cache;
		this._loader = ctx.game.load;

		/**
		 * Asset packs do load multiple keys. These keys with their reference to
		 * the pack key are saved inside this so after a file was loaded the
		 * original cache entry can be found via indirection.
		 */
		this._packKeyCache = {};

		// Add the callbacks.
		this._ctx.game.load.onFileComplete.add(this._fileLoadedCallback, this);
		
		//pubsub.subscribe(DemandLoader.Message.LOAD, this._asyncLoad, this);
		//pubsub.subscribe(DemandLoader.Message.LOAD_PACK, this._asyncLoadPack, this);
	}
	
	/**
	 * Performs an async loading of the given data via the pubsub system.
	 */
	_asyncLoad(_, msg) {
		this.load(msg.data, msg.callback);
	}
	
	/**
	 * Performs an async loading of the given pack via the pubsub system.
	 */
	_asyncLoadPack(_, msg) {
		this.loadPack(msg.data, msg.callback);
	}
	
	_fileLoadedCallback(progress, key) {

		var cacheData = null;

		if (this._cache.hasOwnProperty(key)) {
			cacheData = this._cache[key];
		} else if (this._packKeyCache.hasOwnProperty(key)) {
			// Go the indirection.
			cacheData = this._cache[this._packKeyCache[key]];
			delete this._packKeyCache[key];
		} else {
			// No cache entry found. Probably the file was directly loaded
			// without the use of the demand loader.
			// Skip the callback search.
			return;
		}

		if (cacheData.type === 'pack' && cacheData.toLoad === 0) {
			// Beginning of the pack load.
			var pack = this._phaserCache.getJSON(key);

			// Add all files of this pack to our file list.
			var keyList = pack[key].map(function(x) {
				return x.key;
			});

			// Save the keys inside the pack into the pack key cache with a
			// reference to the main cache key.
			keyList.forEach(function(x) {
				this._packKeyCache[x] = key;
			}, this);

			this._cache[key].items = keyList;
			this._cache[key].toLoad = keyList.length;

			// Start to load all data in this pack.
			this._loader.pack(key, undefined, pack);
			this._loader.start();
		} else {
			cacheData.toLoad--;
			if (cacheData.toLoad === 0) {
				
				// Delete the entry in cache.
				delete this._cache[key];

				cacheData.callbackFns.forEach(function(x) {
					try {
						x();
					} catch (err) {
						console.error('DemandLoader#_fileLoadedCallback: ' + err, x);
					}
				});
				
				// Restart to fetch queued stuff.
				this._loader.start();
			}
		}
	}

	/**
	 * Checks if all the given keys are already inside the cache. Keys can be a
	 * single string or an array of strings containing the the key names.
	 * 
	 * @param {string|string[]} keys
	 */
	has(keys, type) {
		if (Array.isArray(keys)) {

			var hasCache = true;

			keys.forEach(function(val) {
				hasCache = hasCache & this._hasLoaded(val.key, val.type);
			}.bind(this));

			return hasCache;

		} else {
			if (type === undefined) {
				return false;
			}

			return this._hasLoaded(keys, type);
		}
	}

	get(key, type) {

		if (!this._hasLoaded(key, type)) {
			return null;
		}

		switch (type) {
		case 'image':
		case 'item':
			return this._phaserCache.getImage(key);
		case 'json':
			return this._phaserCache.getJSON(key);
		default:
			console.warn('DemandLoader#get: Unknown type.');
			return false;
		}
	}

	/**
	 * Checks if the key for the given type exist inside the loader (the file
	 * was already loaded then).
	 * 
	 * @return TRUE if the key exist. FALSE otherwise.
	 */
	_hasLoaded(key, type) {
		switch (type) {
		case 'image':
		case 'item':
		// Atlas is currently inconsistently handled inside the cache.
		// see https://github.com/photonstorm/phaser/issues/2893
		case 'atlasJSONHash':
			return this._phaserCache.checkImageKey(key);
		case 'json':
			return this._phaserCache.checkJSONKey(key);
		default:
			console.warn('_hasLoaded: Unknown type.');
			return false;

		}
	}

	/**
	 * Loads the pack data formated in a phaser pack data structure.
	 * 
	 * @param {Object} pack A phaserjs asset pack object.
	 * @param {Function} fnOnComplete Callback function called if all files in the asset pack are loaded.
	 */
	loadPackData(pack, fnOnComplete) {
		fnOnComplete = fnOnComplete || NOOP;

		// Get the key. Keys in objects are not sorted but packs should contain
		// only one key.
		// So I guess we re safe.
		var key = '';
		for ( var a in pack) {
			key = a;
			break;
		}

		// Check if a load is running. If this is the case only add the callback
		// function to be executed when the load completes.
		if (this._cache.hasOwnProperty(key)) {
			this._cache[key].callbackFns.push(fnOnComplete);
			return;
		}
		
		// Remove all files from this pack which are already loaded.
		let notLoadedFiles = pack[key].filter(function(file){
			return !this._hasLoaded(file.key, file.type);
		}, this);
		
		if(notLoadedFiles.length === 0) {
			try {
				fnOnComplete();
			} catch(e) {
				LOG.error('Could not execute demand loader callback. ' + e, fnOnComplete);
			}
			return;
		} else {
			LOG.debug('Not all assets loaded. Loading: ', notLoadedFiles);
		}

		// Add all files of this pack to our file list.
		var keyList = notLoadedFiles.map(function(x) {
			return x.key;
		});
		
		// Check if there are

		keyList.forEach(function(x) {
			this._packKeyCache[x] = key;
		}, this);

		this._cache[key] ={
			key : key,
			callbackFns : [ fnOnComplete ],
			toLoad : keyList.length,
			items: keyList,
			type : 'file'
		};

		// Start to load all data in this pack.
		this._loader.pack(key, null, pack);
		this._loader.start();
	}

	/**
	 * Loads the data from the server. The data parameter must be like:
	 * <p>
	 * {key : name, type : 'json', url : url}
	 * </p>.
	 */
	load(data, fnOnComplete) {
		if (Array.isArray(data)) {
			console.error('Loading of arrays not yet supported.');
			return;
		}

		fnOnComplete = fnOnComplete || NOOP;

		// Check if a loading of the current key is already running.
		// If this is the case only add the callback function to be executed
		// when the load completes.
		if (this._cache.hasOwnProperty(data.key)) {
			this._cache[data.key].callbackFns.push(fnOnComplete);
			return;
		}
		
		if (this._hasLoaded(data.key, data.type)) {
			fnOnComplete();
			return;
		}

		switch (data.type) {
		case 'json':
			this._loader.json(data.key, data.url);
			break;
		case 'image':
			this._loader.image(data.key, data.url);
			break;
		default:
			LOG.warn('Loading this type not supported: ' + data.type);
			return;
		}

		let countObj = {
			key : data.key,
			callbackFns : [ fnOnComplete ],
			toLoad : 1,
			type : 'file'
		};

		this._cache[data.key] = countObj;
		this._loader.start();
	}
}

DemandLoader.Message = Object.freeze({
	LOAD : 'loader.load',
	LOAD_PACK : 'loader.pack'
});
