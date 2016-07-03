import MultispriteBuilder from './MultispriteBuilder.js';
import PlayerMultispriteBuilder from './PlayerMultispriteBuilder.js';
import SpriteBuilder from './SpriteBuilder.js';
import SimpleObjectBuilder from './SimpleObjectBuilder.js';
import ItemBuilder from './ItemBuilder.js';
import NOOP from '../../../util/NOOP.js';


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
	
		this.descLoader = new Bestia.Engine.DescriptionLoader(ctx.loader, ctx.url);
	
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
	 * Registers dynamically new builder objects which react upon incoming entity
	 * update messages.
	 */
	register(builder) {
		alert("Geht");
		this.builder.push(builder);
	}

	build(data, fnOnComplete) {
		fnOnComplete = fnOnComplete || NOOP;

		// Do we already have the desc file?
		var descFile = this._getDescriptionFile(data);

		if (descFile === null) {
			// We must first load this file because we dont know anything about the
			// entity. Hand over the now loaded description file as well as the
			// callback.
			this.descLoader.loadDescription(data, this._continueBuild.bind(this, data, fnOnComplete));

		} else {
			this._continueBuild(data, fnOnComplete, descFile);
		}
	}

	_continueBuild(data, fnOnComplete, descFile) {
		var b = this._getBuilder(data, descFile);

		if (!b) {
			console.warn("Could not build entity. From data: " + JSON.stringify(data));
			return;
		}

		b.load(descFile, function() {

			if (descFile === null) {
				// Could not load desc file.
				return;
			}

			var entity = b.build(data, descFile);

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
