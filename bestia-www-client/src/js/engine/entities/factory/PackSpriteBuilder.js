import Builder from './Builder.js';
import MultispriteEntity from '../MultispriteEntity.js';

/**
 * Responsible for building the packed (multisprite) entities. These are usually
 * mobs. All sprites which consists of multiple sprites with animation data.
 */
export default class PackSpriteBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.version = 1;
		
		/**
		 * Url helper.
		 */
		this._url = ctx.url;
	}
	
	build(data, desc) {
		var entity = new MultispriteEntity(this._ctx, data.uuid, desc);

		// Setup the phaser sprite.
		entity.setSprite(data.s);
		entity.setPosition(data.x, data.y);
		
		entity.addToGroup(this._ctx.groups.sprites);

		if (data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}

		return entity;
	}

	/**
	 * Responsible for loading all the necessairy date before a build of the
	 * object can be performt.
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
				type : "atlasJSONHash",
				key : msName,
				textureURL : this._url.getMultiSheetUrl(msName),
				atlasURL : this._url.getMultiAtlasUrl(msName),
				atlasData : null
			});

			// Load the description.
			packArray.push({
				type : "json",
				key : msName + '_desc',
				url : this._url.getMultiDescUrl(msName)
			});

			// Also include the offset file for this combination.
			var offsetFileName = 'offset_' + msName + '_' + key;
			packArray.push({
				type : "json",
				key : offsetFileName,
				url : this._url.getMultiOffsetUrl(msName, offsetFileName)
			});

		}, this);

		return pack;
	}

	canBuild(data) {
		return data.t.toUpperCase() === 'PACK';
	}
}