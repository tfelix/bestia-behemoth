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

	if(!(loader instanceof Phaser.Loader)) {
		throw "DemandLoader: Loader is not a Phaser.Loader";
	}
	
	if(!(cache instanceof Phaser.Cache)) {
		throw "DemandLoader: Cache is not a Phaser.Cache";
	}
	
	this._loader = loader;
	this._pcache = cache;
	
	this._cache = {};
	this._keyCache = [];
	
	// Add the callbacks.
	loader.onFileComplete.add(this._packPreLoadedCallback, this);
};


Bestia.Engine.DemandLoader.prototype._packPreLoadedCallback = function(progress, key) {
	//console.debug("Loaded: "+ key +" ("+ totalLoaded + "/" + totalPacks + ") success: " + success);
	
	// Look if we are preloading a AssetPack.
	var keyIndex = this._keyCache.indexOf(key);
	
	if(keyIndex != -1) {
		this._keyCache.splice(keyIndex, keyIndex + 1);
		
		var pack = this._pcache.getJSON(key);
		
		// Add all files of this pack to our file list.
		var keyList = pack[key].map(function(x){ return x.key; });
		
		this._cache[key].items = keyList;
		this._cache[key].toLoad = keyList.length;
		
		
		// Start to load all data in this pack.
		this._loader.pack(key, undefined, pack);
		this._loader.start();
		
	} else {
		// Loaded a regular file.
		
		// Traverse all lists and see if a asset pack has loaded completely.
		for(var iKey in this._cache) {
			
			var c = this._cache[iKey];
			
			if(c.items.indexOf(key) !== -1) {
				c.toLoad--;
			}
			
			if(c.toLoad === 0) {
				c.callbackFns.forEach(function(x){ 
					x(); 
					
				});
				delete this._cache[iKey];
			}
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

	// Check if a load is running. If this is the case only add the callback function
	// to be executed when the load completes.	
	if(this._cache.hasOwnProperty(key)) {
		this._cache[key].callbackFns.push(fnOnComplete);
		return;
	}
	
	var countObj = {key: key, callbackFns: [fnOnComplete], toLoad: 0, items: []};
	
	this._cache[key] = countObj;
	this._keyCache.push(key);

	var packUrl = Bestia.Urls.assetsMobSprite + key + '_pack.json';

	this._loader.json(key, packUrl);
	//this._loader.pack(key, packUrl);
	this._loader.start();
};

/**
 * 
 * @param {Function}
 *            fnOnComplete - Callback function which will be called if the
 *            file(s) have been loaded.
 */
Bestia.Engine.DemandLoader.prototype.loadItemSprite = function(key, fnOnComplete) {

	// Check if a load is running. If this is the case only add the callback function
	// to be executed when the load completes.	
	if(this._cache.hasOwnProperty(key)) {
		this._cache[key].callbackFns.push(fnOnComplete);
		return;
	}
	
	var countObj = {key: key, callbackFns: [fnOnComplete], toLoad: 0, items: []};
	
	this._cache[key] = countObj;
	this._keyCache.push(key);

	var packUrl = Bestia.Urls.assetsRoot + 'img/items/ '+ key + '.png';

	this._loader.image(key, packUrl);
	this._loader.start();
};