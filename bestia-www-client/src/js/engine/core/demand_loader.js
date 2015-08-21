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
Bestia.Engine.DemandLoader = function(loader) {

	if(!(loader instanceof Phaser.Loader)) {
		throw "DemandLoader: Loader is not a Phaser.Loader";
	}
	
	this._loader = loader;

	this._cache = {};
	this._keyCache = {};
	
	// Add the callbacks.
	loader.onPackComplete.add(this._packLoadedCallback, this);
};

/**
 * Private callback for the loader if a pack has completely loaded. We then check
 * with our cache which function callbacks are waiting for this event and notify
 * them.
 */
Bestia.Engine.DemandLoader.prototype._packLoadedCallback = function(key, success, totalLoaded, totalPacks) {
	console.debug("Loaded: "+ key +" ("+ totalLoaded + "/" + totalPacks + ") success: " + success);
	
	var uniqueKey = this._keyCache[key];
	var fns = this._cache[uniqueKey];
	
	// Trigger all the cached callbacks.
	fns.forEach(function(fn){ fn(); }, this);
	
	// Remove all callbacks and keys.
	delete this._keyCache[key];
	delete this._cache[uniqueKey];
};

/**
 * 
 * @param {Function}
 *            fnOnComplete - Callback function which will be called if the
 *            file(s) have been loaded.
 */
Bestia.Engine.DemandLoader.prototype.loadMobSprite = function(key, fnOnComplete) {

	var uniqueKey = "mob-" + key;
	if (uniqueKey in this._cache) {
		this._cache[uniqueKey].push(fnOnComplete);
	} else {
		this._cache[uniqueKey] = [ fnOnComplete ];
		this._keyCache[key] = uniqueKey;
	}

	var packUrl = Bestia.Urls.assetsMobSprite + key + '_pack.json';

	this._loader.pack(key, packUrl);
	this._loader.start();
};