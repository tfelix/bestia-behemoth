import SpriteBuilder from './SpriteBuilder';
import DynamicSpriteBuilder from './DynamicSpriteBuilder.js';
import NOOP from '../../../util/NOOP.js';
import LOG from '../../../util/Log';
import DescriptionLoader from '../../DescriptionLoader.js';
import ComponentNames from '../ComponentNames';


/**
 * The factory is responsible for loading all the needed assets to display a
 * certain entity. It resolves if it is a bestia, sprite, item etc. entity and
 * uses the correct javascript class to manage it. It gets added to the entity
 * cache to receive updates.
 * 
 * @author Thomas Felix
 */
export default class EntityFactory {

	constructor(ctx) {

		this.descLoader = new DescriptionLoader(ctx);
		this._descCache = ctx.descriptionCache;

		/**
		 * Registry for the builder to register themselfes.
		 */
		this.builder = [];

		this.register(new SpriteBuilder(game));
		this.register(new DynamicSpriteBuilder(game));
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
	 * @param {Function} fnOnComplete - Callback function getting the entity reference when the load process was completed.
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
	 * @param {Function} fnOnComplete - Callback function getting the entity reference when the build process was completed.
	 */
	build(data, fnOnComplete) {
		LOG.debug('Building entity: ' + JSON.stringify(data));

		fnOnComplete = fnOnComplete || NOOP;

		// Do we already have the desc file?
		var descFile = this.descLoader.getDescription(data);

		if (descFile === null) {
			// We must first load this file because we dont know anything about
			// the entity. Hand over the now loaded description file as well as
			// the callback.
			LOG.debug('Description not found. Loading it.');
			this.descLoader.loadDescription(data, function (descFile) {
				// Description file was loaded. We can now store it.
				this._descCache.addSpriteDescription(descFile);
				this._continueBuildWithDescription(data, fnOnComplete, descFile);
			}.bind(this));

		} else {
			LOG.debug('Description present building entity.');
			this._continueBuildWithDescription(data, fnOnComplete, descFile);
		}
	}

	/**
	 * After we got the initial description file we now must continue the
	 * creation of the entity.
	 */
	_continueBuildWithDescription(data, fnOnComplete, descFile) {
		if (descFile === null) {
			// Could not load desc file.
			LOG.warn('Could not load description file from data: ' + JSON.stringify(data));
			fnOnComplete(null);
			return;
		}

		var builder = this._getBuilder(data, descFile);

		if (!builder) {
			LOG.warn('No builder registered to build entity from data: ' + JSON.stringify(data));
			fnOnComplete(null);
			return;
		}

		// The builder is now responsible for figuring out which files to load
		// additionally. It must be all given in the JSON file.
		builder.load(descFile, function () {

			// Abort if there is only loading required.
			if (data.onlyLoad === true) {
				LOG.debug('Aborting entity creation. Data was only loaded.');
				fnOnComplete(null);
				return;
			}

			// Do some sanity checks.
			if (!data.eid) {
				throw 'No eid (entity id) is given.';
			}

			let positionComp = data.components[ComponentNames.POSITION];
			if (!positionComp.position.x || !positionComp.position.y) {
				throw 'No x and/or y (x, y) postion is given';
			}

			let visComp = data.components[ComponentNames.VISIBLE];
			if (!visComp.visual.sprite) {
				throw 'No spritename (s) given';
			}

			let entity = null;

			try {
				entity = builder.build(data, descFile);
			} catch (err) {
				LOG.warn('Error while build sprite: ' + err);
				entity = null;
			}

			// Call the callback handler.
			try {
				fnOnComplete(entity);
			} catch (err) {
				LOG.warn('Could not perform callback after entity was build.', fnOnComplete);
				LOG.warn('Error: ', err);
			}

		}.bind(this));
	}

	/**
	 * Returns a responsible builder for the given data and description file.
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
