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

	// Add the callbacks.
	loader.onFileComplete.add(this._fileLoadedCallback, this);
};

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
				x();
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
	if(Array.isArray(keys)) {
		
		var hasCache = true;
		
		keys.forEach(function(val){
			hasCache = hasCache & this._hasType(val.key, val.type);
		}.bind(this));
		
		return hasCache;
		
	} else {
		if(type === undefined) {
			return false;
		}
		
		return this._hasType(keys, type);
	}
};

Bestia.Engine.DemandLoader.prototype._hasType = function(key, type) {
	switch (type) {
	case 'item':
		return this._phaserCache.checkImageKey(key);
	default:
		console.warn("_hasType: Unknown type.");
		return false;

	}
};

/**
 * 
 * @param {Function}
 *            fnOnComplete - Callback function which will be called if the
 *            file(s) have been loaded.
 */
Bestia.Engine.DemandLoader.prototype.loadMobSprite = function(key, fnOnComplete) {

	// Check if a load is running. If this is the case only add the callback
	// function to be executed when the load completes.
	if (this._cache.hasOwnProperty(key)) {
		this._cache[key].callbackFns.push(fnOnComplete);
		return;
	}

	var countObj = {
		key : key,
		callbackFns : [ fnOnComplete ],
		toLoad : 0,
		items : [],
		type : 'pack'
	};
	this._cache[key] = countObj;

	var packUrl = this._urlHelper.getMobPackUrl(key);

	this._loader.json(key, packUrl);
	this._loader.start();
};

/**
 * 
 * @param {Function}
 *            fnOnComplete - Callback function which will be called if the
 *            file(s) have been loaded.
 */
Bestia.Engine.DemandLoader.prototype.loadItemSprite = function(key, fnOnComplete) {

	// First check if we actually have not yet loaded the assets.
	if (this._phaserCache.checkImageKey(key)) {
		fnOnComplete();
		return;
	}

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

	var imageUrl = this._urlHelper.getItemIconUrl(key);
	this._loader.image(key, imageUrl);
	this._loader.start();
};