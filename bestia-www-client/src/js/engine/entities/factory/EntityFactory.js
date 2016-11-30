import MultispriteBuilder from './MultispriteBuilder.js';
import PlayerMultispriteBuilder from './PlayerMultispriteBuilder.js';
import SpriteBuilder from './SpriteBuilder.js';
import SimpleObjectBuilder from './SimpleObjectBuilder.js';
import ItemBuilder from './ItemBuilder.js';
import NOOP from '../../../util/NOOP.js';
import DescriptionLoader from '../../core/DescriptionLoader.js';


/**
 * The factory is responsible for loading all the needed assets to display a
 * certain entity. It resolves if it is a bestia, sprite, item etc. entity and
 * uses the correct javascript class to manage it. It gets added to the entity
 * cache to receive updates.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
export default class EntityFactory {
	
	constructor(ctx) {

		if (!ctx) {
			throw new Error("Context can not be null.");
		}
	
		this._ctx = ctx;
	
		this.descLoader = new DescriptionLoader(ctx.loader, ctx.url);
	
		/**
		 * Registry for the builder to register themselfes.
		 */
		this.builder = [];
	
		this.builder.push(new MultispriteBuilder(this, ctx));
		this.builder.push(new PlayerMultispriteBuilder(this, ctx));
		this.builder.push(new SpriteBuilder(this, ctx));
		this.builder.push(new SimpleObjectBuilder(this, ctx));
		this.builder.push(new ItemBuilder(this, ctx));
	}
	
	/**
	 * Registers dynamically new builder objects which react upon incoming
	 * entity update messages.
	 */
	register(builder) {
		this.builder.push(builder);
	}

	/**
	 * This will create a new entity sprite object. Bascially it loads all
	 * needed assets and wraps a phaser sprite (or a group of them) into our own
	 * entity handling class. If the onlyLoad flag is set the building will not
	 * be done but there will be only the data loaded and cached. This can be
	 * used when to only load the sprite data which might be needed for
	 * preloading form a different game state.
	 * 
	 * @param {boolean}
	 *            onlyLoad - Flag of the builder should only load the assets.
	 */
	build(data, fnOnComplete, onlyLoad) {
		onlyLoad = onlyLoad || false;
		fnOnComplete = fnOnComplete || NOOP;
		
		// Add the flag to the data object for taking it to the builder.
		data.onlyLoad = onlyLoad;

		// Do we already have the desc file?
		var descFile = this._getDescriptionFile(data);

		if (descFile === null) {
			// We must first load this file because we dont know anything about
			// the entity. Hand over the now loaded description file as well as
			// the callback.
			this.descLoader.loadDescription(data, this._continueBuild.bind(this, data, fnOnComplete));

		} else {
			this._continueBuild(data, fnOnComplete, descFile);
		}
	}

	/**
	 * After we got the initial description file we now must continue the
	 * creation of the entity.
	 */
	_continueBuild(data, fnOnComplete, descFile) {
		var b = this._getBuilder(data, descFile);

		if (!b) {
			console.warn("Could not build entity. From data: " + JSON.stringify(data));
			return;
		}

		// The builder is now responsible for figuring out which files to load
		// additionally. It must be all given in the JSON file.
		b.load(descFile, function() {

			if (descFile === null) {
				// Could not load desc file.
				console.warn("Could not load description file from data: " + JSON.stringify(data));
				return;
			}

			let entity = b.build(data, descFile);

			// Entity might be null if the onlyLoad flag was set. So we need to
			// check.
			if(entity !== null) {
				this._ctx.entityCache.addEntity(entity);
			}			

			// Call the callback handler.
			fnOnComplete(entity);
		}.bind(this));
	}

	_getDescriptionFile(data) {
		if (data.t === 'STATIC') {
			// We can generate the description file on the fly.
			// TODO This should be externalized.
			return {
				type : 'STATIC',
				version : 1,
				name : data.s
			};
		} else {
			return this.descLoader.getDescription(data);
		}
	}

	/**
	 * Das müsste auch an die Builder ausgelagert werden.
	 */
	_getBuilder(data, descFile) {
		for (var i = 0; i < this.builder.length; i++) {
			if (this.builder[i].canBuild(data, descFile)) {
				return this.builder[i];
			}
		}

		return null;
	}
}
