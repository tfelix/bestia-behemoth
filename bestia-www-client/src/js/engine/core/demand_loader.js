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
Bestia.Engine.DemandLoader = function(loader, cache) {

	if (!(loader instanceof Phaser.Loader)) {
		throw "DemandLoader: Loader is not a Phaser.Loader";
	}

	if (!(cache instanceof Phaser.Cache)) {
		throw "DemandLoader: Cache is not a Phaser.Cache";
	}

	this._loader = loader;
	this._phaserCache = cache;

	this._cache = {};
	this._keyCache = {};

	// Add the callbacks.
	loader.onFileComplete.add(this._fileLoadedCallback, this);
};

Bestia.Engine.DemandLoader.prototype._fileLoadedCallback = function(progress, key) {

	var cacheData = null;
	
	if(this._cache.hasOwnProperty(key)) {
		cacheData = this._cache[key];
	} else {
		// Go the indirection.
		cacheData = this._cache[this._keyCache[key]];
		delete this._keyCache[key];
	}

	if (cacheData.type === 'pack' && cacheData.toLoad === 0) {
		// Beginning of the pack load.
		var pack = this._phaserCache.getJSON(key);

		// Add all files of this pack to our file list.
		var keyList = pack[key].map(function(x) {
			return x.key;
		});
		
		keyList.forEach(function(x){
			this._keyCache[x] = key;
		}, this);

		this._cache[key].items = keyList;
		this._cache[key].toLoad = keyList.length;

		// Start to load all data in this pack.
		this._loader.pack(key, undefined, pack);
		//this._loader.start();
	} else {
		cacheData.toLoad--;
		if (cacheData.toLoad === 0) {
			cacheData.callbackFns.forEach(function(x) {
				x();
			});
			delete this._cache[key];
		}
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

	var packUrl = Bestia.Urls.assetsMobSprite + key + '_pack.json';

	this._loader.json(key, packUrl);
	//this._loader.start();
};

/**
 * 
 * @param {Function}
 *            fnOnComplete - Callback function which will be called if the
 *            file(s) have been loaded.
 */
Bestia.Engine.DemandLoader.prototype.loadItemSprite = function(key, fnOnComplete) {
	
	// First check if we actually have not yet loaded the assets.
	if(this._phaserCache.checkImageKey(key)) {
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

	var imageUrl = Bestia.Urls.assetsRoot + 'img/items/' + key + '.png';
	this._loader.image(key, imageUrl);
	this._loader.start();
};