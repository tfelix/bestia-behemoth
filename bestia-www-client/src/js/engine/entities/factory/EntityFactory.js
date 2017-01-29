import PackSpriteBuilder from './PackSpriteBuilder.js';
import DynamicSpriteBuilder from './DynamicSpriteBuilder.js';
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
	
		this.descLoader = new DescriptionLoader(ctx);
		
		this._ctx = ctx;
	
		/**
		 * Registry for the builder to register themselfes.
		 */
		this.builder = [];

		this.register(new PackSpriteBuilder(this, ctx));
		this.register(new DynamicSpriteBuilder(this, ctx));
		this.register(new SpriteBuilder(this, ctx));
		this.register(new SimpleObjectBuilder(this, ctx));
		this.register(new ItemBuilder(this, ctx));
	}
	
	/**
	 * Registers dynamically new builder objects which react upon incoming
	 * entity update messages.
	 */
	register(builder) {
		this.builder.push(builder);
	}
	
	/**
	 * This will only load the assets specified in the given data set. The
	 * callback function is executed after all loads have been performed.
	 * 
	 * @param {Function}
	 *            fnOnComplete - Callback function getting the entity reference
	 *            when the load process was completed.
	 */
	load(data, fnOnComplete) {
		fnOnComplete = fnOnComplete || NOOP;
		
		// Add the flag to the data object for taking it to the builder.
		data.onlyLoad = true;
		
		this.build(data, fnOnComplete);
	}

	/**
	 * This will create a new entity sprite object. Bascially it loads all
	 * needed assets and wraps a phaser sprite (or a group of them) into our own
	 * entity handling class. If the onlyLoad flag is set the building will not
	 * be done but there will be only the data loaded and cached. This can be
	 * used when to only load the sprite data which might be needed for
	 * preloading form a different game state.
	 * 
	 * @param {Function}
	 *            fnOnComplete - Callback function getting the entity reference
	 *            when the build process was completed.
	 * @param {boolean}
	 *            onlyLoad - Flag of the builder should only load the assets.
	 */
	build(data, fnOnComplete) {
		fnOnComplete = fnOnComplete || NOOP;

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
			console.warn('No builder registered to build entity from data: ' + JSON.stringify(data));
			fnOnComplete(null);
			return;
		}
		
		if (descFile === null) {
			// Could not load desc file.
			console.warn('Could not load description file from data: ' + JSON.stringify(data));
			fnOnComplete(null);
			return;
		}

		// The builder is now responsible for figuring out which files to load
		// additionally. It must be all given in the JSON file.
		b.load(descFile, function() {
			
			// Abort if there is only loading required.
			if(data.onlyLoad === true) {
				fnOnComplete(null);
				return;
			}
			
			// Do some sanity checks.
			if(data.eid === undefined) {
				throw 'No eid (entity id) is given.';
			}
			
			if(data.x === undefined || data.y === undefined) {
				throw 'No x and/or y (x, y) postion is given';
			}
			
			if(!data.s) {
				throw 'No spritename (s) given';
			}
			
			if(!data.a) {
				throw 'No action (a) given';
			}
			
			let entity = b.build(data, descFile);
			this._ctx.entityCache.addEntity(entity);	

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
