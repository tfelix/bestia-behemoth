import Builder from './Builder.js';
import groups, { GROUP_LAYERS } from '../../Groups';
import LOG from '../../../util/Log';
import { setupSpriteAnimation } from '../traits/VisualTrait';
import WorldHelper from '../../map/WorldHelper';
import ComponentNames from '../ComponentNames';
import { engineContext, descriptionCache } from '../../EngineData';
import { addSubsprite } from '../traits/VisualTrait';

//const NULL_OFFSET = { x: 0, y: 0 };

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

		let posComp = data.components[ComponentNames.POSITION];
		var pos = WorldHelper.getPxXY(posComp.position.x + 0.5, posComp.position.y + 0.9);
		var sprite = this._game.add.sprite(pos.x, pos.y, desc.name);

		groups.get(GROUP_LAYERS.SPRITES).add(sprite);

		setupSpriteAnimation(sprite, desc);

		// Add the multi sprites if there are some of them.
		var multisprites = desc.multiSprite || [];

		multisprites.forEach(function (msName) {
			LOG.debug('Adding multisprite to main sprite: ' + msName);

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

			let msSprite = this._game.make.sprite(0, 0, msName);

			let defaultCords = offsets.defaultCords || {
				x: 0,
				y: 0
			};
			msSprite.x = defaultCords.x;
			msSprite.y = defaultCords.y;

			// Setup the normal data.
			setupSpriteAnimation(msSprite, msDesc);

			addSubsprite(sprite, msSprite);

		}, this);

		return sprite;
	}

	/**
	 * Responsible for loading all the needed date before a build of the object
	 * can be performed.
	 */
	load(descFile, fnOnComplete) {

		var pack = this._extendPack(descFile);
		engineContext.loader.loadPackData(pack, function () {

			// Iterate over all the keys of the pack since it is of the pattern:
			// mastersmith : 
			//	0: {type: "atlasJSONHash", key: "mastersmith", textureURL: "assets/sprite/mob/mastersmith/mastersmith.png", atlasURL: "../mastersmith/mastersmith.json"}
			//	1: {type: "atlasJSONHash", key: "female_01", textureURL: "assets/sprite/multi/female_01/female_01.png", atlasURL: ".../female_01/female_01.json",}
			//
			for (var key in pack) {
				if (pack.hasOwnProperty(key)) {
					
					var dataArray = pack[key];
					
					dataArray.forEach(function(entry){
						
						if(entry.key.indexOf('offset') === 0 && entry.type === 'json') {
							// Starts with the string offset to the key is a offset subsprite description.
							var offsetData = this._game.cache.getJSON(entry.key);
							// TODO subsprite name automatisch auslesen.
							descriptionCache.addSubspriteOffset('female_01', offsetData);
						}

					}, this);
				}
			}
			
			// Call original callback fn.
			fnOnComplete();
		}.bind(this));
	}

	/**
	* Extends the given pack with multisprite data. Also dynamic multisprites
	* can be requested by setting the additional sprite array.
	* 
	* @param descFile
	* @param additionalSprites
	* @returns
	*/
	_extendPack(descFile, additionalSprites = []) {

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
		return data.components[ComponentNames.VISIBLE].visual.type.toUpperCase() === 'DYNAMIC';
	}

	_getOffsetFilename(multispriteName, mainspriteName) {
		return 'offset_' + multispriteName + '_' + mainspriteName;
	}
}