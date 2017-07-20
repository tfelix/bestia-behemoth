import Builder from './Builder.js';
import SpriteEntity from '../SpriteEntity';
import LOG from '../../../util/Log';
import Signal from '../../../io/Signal';

/**
 * Responsible for building the packed (multisprite) entities. These are usually
 * mobs. All sprites which consists of multiple sprites with animation data.
 */
export default class SpriteBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.version = 1;
		
		/**
		 * Url helper.
		 */
		this._url = ctx.url;
		
		this._pubsub = ctx.pubsub;
	}
	
	build(data, desc) {
		LOG.debug('Building pack sprite.', data);
		var entity = new SpriteEntity(this._ctx, data.eid, data.x, data.y, desc);
		
		entity.addToGroup(this._ctx.groups.sprites);
		
		// We need to check how we can interact with this entity. If this is
		// clear we add the behaviour of the move over. Currently its attack
		// only.
		entity.onInputOver = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_over', entity: entity});
		}.bind(this);
		
		entity.onInputOut = function() {
			this._pubsub.publish(Signal.ENGINE_REQUEST_INDICATOR, {handle: 'basic_attack_out', entity: entity});
		}.bind(this);

		if (data.a === 'APPEAR') {
			entity.appear();
		} else {
			entity.show();
		}

		// Switch to idle animation.
		entity.playAnimation('stand_down');

		return entity;
	}

	/**
	 * Responsible for loading all the needed date before a build of the object
	 * can be performed.
	 * 
	 * @param data
	 */
	load(descFile, fnOnComplete) {

		var pack = this._extendPack(descFile);
		this._ctx.loader.loadPackData(pack, fnOnComplete);

	}

	/**
	 * Extends the given pack with multisprite data. Also dynamic multisprites
	 * can be requested by setting the additional sprite array.
	 * 
	 * @param descFile
	 * @param additionalSprites
	 * @returns
	 */
	_extendPack(descFile, additionalSprites) {

		additionalSprites = additionalSprites || [];

		var pack = descFile.assetpack;
		var key = descFile.name;

		var packArray = pack[key];

		var msprites = descFile.multiSprite || [];

		msprites.concat(additionalSprites).forEach(function(msName) {

			// Load the sprite.
			packArray.push({
				type : 'atlasJSONHash',
				key : msName,
				textureURL : this._url.getMultiSheetUrl(msName),
				atlasURL : this._url.getMultiAtlasUrl(msName),
				atlasData : null
			});

			// Load the description.
			packArray.push({
				type : 'json',
				key : msName + '_desc',
				url : this._url.getMultiDescUrl(msName)
			});

			// Also include the offset file for this combination.
			var offsetFileName = 'offset_' + msName + '_' + key;
			packArray.push({
				type : 'json',
				key : offsetFileName,
				url : this._url.getMultiOffsetUrl(msName, offsetFileName)
			});

		}, this);

		return pack;
	}

	canBuild(data) {
		return data.s.t.toUpperCase() === 'PACK';
	}
}