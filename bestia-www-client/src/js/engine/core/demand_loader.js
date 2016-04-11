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
Bestia.Engine.DemandLoader = function(loader, cache, urlHelper) {

	if (!(loader instanceof Phaser.Loader)) {
		throw "DemandLoader: Loader is not a Phaser.Loader";
	}

	if (!(cache instanceof Phaser.Cache)) {
		throw "DemandLoader: Cache is not a Phaser.Cache";
	}

	if (!urlHelper) {
		throw "UrlHelper: Can not be null";
	}

	this._loader = loader;
	this._phaserCache = cache;
	this._urlHelper = urlHelper;

	this._cache = {};
	this._keyCache = {};

	// this._loadPackCallBuffer = [];

	// Add the callbacks.
	loader.onFileComplete.add(this._fileLoadedCallback, this);
	// loader.onLoadComplete.add(this._checkPackCallBufferCallback, this);
};

/**
 * Adding asset packs to the queue while the queue is loading will mess up the
 * loading process. We must therefore wait and call when loading has stopped.
 */
/*
 * Bestia.Engine.DemandLoader.prototype._checkPackCallBufferCallback =
 * function() {
 * 
 * if(this._loadPackCallBuffer.length == 0) { // Nothing to do. return; }
 * 
 * 
 * var fn = this._loadPackCallBuffer.pop(); // Calling the callback will start
 * the load. Thus we need to wait again. fn(); };
 */

Bestia.Engine.DemandLoader.prototype._fileLoadedCallback = function(progress, key) {

	var cacheData = null;

	if (this._cache.hasOwnProperty(key)) {
		cacheData = this._cache[key];
	} else if (this._keyCache.hasOwnProperty(key)) {
		// Go the indirection.
		cacheData = this._cache[this._keyCache[key]];
		delete this._keyCache[key];
	} else {
		// No cache entry found. Propably the file was directly loaded without
		// the use of the demand loader.
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

		keyList.forEach(function(x) {
			this._keyCache[x] = key;
		}, this);

		this._cache[key].items = keyList;
		this._cache[key].toLoad = keyList.length;

		// Start to load all data in this pack.
		this._loader.pack(key, undefined, pack);
		this._loader.start();
	} else {
		cacheData.toLoad--;
		if (cacheData.toLoad === 0) {

			cacheData.callbackFns.forEach(function(x) {
				try {
					x();
				} catch (err) {
					console.error("DemandLoader#_fileLoadedCallback: " + err);
				}
			});

			delete this._cache[key];
			// Restart to fetch queued stuff.
			this._loader.start();
		}
	}

};

/**
 * Checks if all the given keys are already inside the cache. Keys can be a
 * single string or an array of strings containing the the key names.
 * 
 * @param keys
 *            {string|array}
 */
Bestia.Engine.DemandLoader.prototype.has = function(keys, type) {
	if (Array.isArray(keys)) {

		var hasCache = true;

		keys.forEach(function(val) {
			hasCache = hasCache & this._hasType(val.key, val.type);
		}.bind(this));

		return hasCache;

	} else {
		if (type === undefined) {
			return false;
		}

		return this._hasType(keys, type);
	}
};

Bestia.Engine.DemandLoader.prototype.get = function(key, type) {

	if (!this._hasType(key, type)) {
		return null;
	}

	switch (type) {
	case 'item':
		return this._phaserCache.getImage(key);
	case 'json':
		return this._phaserCache.getJSON(key);
	default:
		console.warn("DemandLoader#get: Unknown type.");
		return false;
	}
};

Bestia.Engine.DemandLoader.prototype._hasType = function(key, type) {
	switch (type) {
	case 'item':
		return this._phaserCache.checkImageKey(key);
	case 'json':
		return this._phaserCache.checkJSONKey(key);
	default:
		console.warn("_hasType: Unknown type.");
		return false;

	}
};

Bestia.Engine.DemandLoader.prototype.loadPackData = function(pack, fnOnComplete) {

	// If there is currently a loading in progress we can not insert our pack
	// data. This will mess up the Phaser Loader. We MUST wait until the loading
	// has finished. DOES NOT WORK.
	/*
	 * if (this._loader.isLoading) { this._loadPackCallBuffer.push(function() {
	 * this.loadPackData(pack, fnOnComplete); }.bind(this)); return; }
	 */

	fnOnComplete = fnOnComplete || Bestia.NOOP;

	// Get the key. Keys in objects are not sorted but packs should contain only
	// one key.
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

	// Add all files of this pack to our file list.
	var keyList = pack[key].map(function(x) {
		return x.key;
	});

	keyList.forEach(function(x) {
		this._keyCache[x] = key;
	}, this);

	var countObj = {
		key : key,
		callbackFns : [ fnOnComplete ],
		toLoad : 1,
		type : 'file'
	};
	this._cache[key] = countObj;

	this._cache[key].items = keyList;
	this._cache[key].toLoad = keyList.length;

	// Start to load all data in this pack.
	this._loader.pack(key, null, pack);
	this._loader.start();
};

Bestia.Engine.DemandLoader.prototype.load = function(data, fnOnComplete) {
	if (Array.isArray(data)) {
		console.error("Loading of arrays not yet supported.");
		return;
	}

	fnOnComplete = fnOnComplete || Bestia.NOOP;

	// Temp.
	var key = data.key;

	// Check if a load is running. If this is the case only add the callback
	// function to be executed when the load completes.
	if (this._cache.hasOwnProperty(key)) {
		this._cache[key].callbackFns.push(fnOnComplete);
		return;
	}

	var countObj = {
		key : key,
		callbackFns : [ fnOnComplete ],
		toLoad : 1,
		type : 'file'
	};

	this._cache[key] = countObj;

	switch (data.type) {
	case 'json':
		this._loader.json(data.key, data.url);
		break;
	default:
		console.warn("Loading this type not supported: " + data.type);
		return;
	}

	this._loader.start();
};
