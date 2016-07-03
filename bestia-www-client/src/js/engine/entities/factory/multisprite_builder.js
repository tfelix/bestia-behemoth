import Builder from './Builder.js';
import MultispriteEntity from '../MultispriteEntity.js';

/**
 * Responsible for building the multisprites entities.
 */

export default class MultispriteBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.version = 1;
	}
	
	build(data, desc) {

		var entity = new MultispriteEntity(this._ctx, data.uuid, desc);

		// Position
		entity.setPosition(data.x, data.y);

		entity.setSprite(data.s);
		
		entity.addToGroup(this._ctx.groups.sprites);

		if (data.a === "APPEAR") {
			entity.appear();
		} else {
			entity.show();
		}

		return entity;
	}

	/**
	 * Responsible for loading all the necessairy date before a build of the object
	 * can be performt.
	 * 
	 * @param data
	 */
	load(descFile, fnOnComplete) {

		var pack = this._extendPack(descFile);
		this._ctx.loader.loadPackData(pack, fnOnComplete);

	}

	/**
	 * Extends the given pack with multisprite data. Also dynamic multisprites can
	 * be requested by setting the additional sprite array.
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
				textureURL : "assets/sprite/multi/" + msName + "/" + msName + ".png",
				atlasURL : "assets/sprite/multi/" + msName + "/" + msName + ".json",
				atlasData : null
			});

			// Load the description.
			packArray.push({
				type : "json",
				key : msName + '_desc',
				url : "assets/sprite/multi/" + msName + "/" + msName + '_desc.json'
			});

			// Also include the offset file for this combination.
			var offsetFileName = Bestia.Engine.MultispriteEntity.getOffsetFilename(msName, key);
			packArray.push({
				type : "json",
				key : offsetFileName,
				url : "assets/sprite/multi/" + msName + "/" + offsetFileName + ".json"
			});

		}, this);

		return pack;
	}

	canBuild(data) {
		return data.t === 'MOB_ANIM';
	}
}