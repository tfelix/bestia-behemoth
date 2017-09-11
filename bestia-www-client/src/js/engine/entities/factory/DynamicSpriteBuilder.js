import Builder from './Builder.js';
import groups, { GROUP_LAYERS } from '../../Groups';
import LOG from '../../../util/Log';
import { setupSpriteAnimation } from '../traits/VisualTrait';
import WorldHelper from '../../map/WorldHelper';
import { engineContext } from '../../EngineData';
import { addSubsprite } from '../traits/VisualTrait';

const NULL_OFFSET = { x: 0, y: 0 };

/**
 * This is able to create sprite entities which differ to the runtime. It must
 * react automatically when created to data delivered by the server. A player
 * sprite is a good example: the hair type is only known to the server at
 * runtime. The sprite will be created based upon this data.
 * This entity is a bit different. It supports a head sprite which can be moved
 * async to the body sprite to give a certain more realistic look to the players
 * then the mobs.
 */
export default class DynamicSpriteBuilder extends Builder {

	constructor(game) {
		super();

		// Register with factory.
		this.type = 'dynamic';
		this.version = 1;

		this._game = game;
	}

	build(data, desc) {
		LOG.debug('Building: ' + JSON.stringify(data) + ' (dynamic sprite)');

		var pos = WorldHelper.getPxXY(data.position.x + 0.5, data.position.y + 0.9);
		var sprite = this._game.add.sprite(pos.x, pos.y, desc.name);

		groups.get(GROUP_LAYERS.SPRITES).add(sprite);

		setupSpriteAnimation(sprite, desc);

		// Add the multi sprites if there are some of them.
		var multisprites = desc.multiSprite || [];

		multisprites.forEach(function (msName) {
			LOG.debug('Adding multisprite: ' + msName);

			// Get the desc file of the multisprite.
			var msDescName = msName + '_desc';
			var msDesc = this._game.cache.getJSON(msDescName);

			// Generate offset information.
			let offsetFileName = this._getOffsetFilename(msName, desc.name);
			let offsets = this._game.cache.getJSON(offsetFileName) || {};

			// Was not loaded. Should not happen.
			if (msDesc == null) {
				LOG.warn('Subsprite description was not loaded. This should not happen: ' + msDescName);
				return;
			}

			let defaultCords = offsets.defaultCords || {
				x: 0,
				y: 0
			};
			
			let msSprite = this._game.make.sprite(0, 0, msName);
			let anchor = msDesc.anchor || { x: 0, y: 0 };
			
			msSprite.anchor = anchor;
			msSprite.x = defaultCords.x;
			msSprite.y = defaultCords.y;

			// TODO This should be automatically parsed.
			// Setup the normal data.
			msSprite.scale.setTo(msDesc.scale || 1);
			msSprite.animations.add('bottom.png', ['bottom.png'], 0, true, false);
			msSprite.animations.add('bottom_left.png', ['bottom_left.png'], 0, true, false);
			msSprite.animations.add('left.png', ['left.png'], 0, true, false);
			msSprite.animations.add('left.png', ['left.png'], 0, true, false);
			msSprite.animations.add('top.png', ['top.png'], 0, true, false);
			msSprite.animations.add('top_left.png', ['top_left.png'], 0, true, false);

			// Prepare the info object.	
			let msData = {
				offsets: offsets.offsets || [],
				name: msName,
				defaultCords: defaultCords
			};

			addSubsprite(sprite, msSprite, msData);
		}, this);

		return sprite;
	}

	/**
	 * Responsible for loading all the needed date before a build of the object
	 * can be performed.
	 * 
	 * @param data
	 */
	load(descFile, fnOnComplete) {

		var pack = this._extendPack(descFile);
		engineContext.loader.loadPackData(pack, fnOnComplete);
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

		msprites.concat(additionalSprites).forEach(function (msName) {

			// Load the sprite.
			packArray.push({
				type: 'atlasJSONHash',
				key: msName,
				textureURL: engineContext.url.getMultiSheetUrl(msName),
				atlasURL: engineContext.url.getMultiAtlasUrl(msName),
				atlasData: null
			});

			// Load the description.
			packArray.push({
				type: 'json',
				key: msName + '_desc',
				url: engineContext.url.getMultiDescUrl(msName)
			});

			// Also include the offset file for this combination.
			var offsetFileName = 'offset_' + msName + '_' + key;
			packArray.push({
				type: 'json',
				key: offsetFileName,
				url: engineContext.url.getMultiOffsetUrl(msName, offsetFileName)
			});

		}, this);

		return pack;
	}

	/**
	 * The type of the entities does now not match the sane check. It must be
	 * corrected.
	 */
	canBuild(data) {
		return data.sprite.type === 'DYNAMIC';
	}

	_getOffsetFilename(multispriteName, mainspriteName) {
		return 'offset_' + multispriteName + '_' + mainspriteName;
	}

	/**
	 * Searches for the data of the subsprite.
	 */
	_getSubspriteData(subspriteName) {
		for (let i = 0; i < this._multiSprites.length; i++) {
			if (this._multiSprites[i].name === subspriteName) {
				return this._multiSprites[i];
			}
		}
		return null;
	}
}