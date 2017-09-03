import Builder from './Builder.js';
import groups, { GROUP_LAYERS } from '../../Groups';
import LOG from '../../../util/Log';
import { setupSpriteAnimation } from '../SpriteAnimationHelper';
import WorldHelper from '../../map/WorldHelper';

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

		var pos = WorldHelper.getPxXY(data.position.x + 0.5, data.position.y + 0.5);
		var sprite = this._game.add.sprite(pos.x, pos.y, desc.name);

		sprite.bType = 'multi';

		groups.get(GROUP_LAYERS.SPRITES).add(sprite);

		// Add the multi sprites if there are some of them.
		var multisprites = desc.multiSprite || [];

		multisprites.forEach(function (msName) {

			// Get the desc file of the multisprite.
			var msDescName = msName + '_desc';
			var msDesc = this._game.cache.getJSON(msDescName);

			// Was not loaded. Should not happen.
			if (msDesc == null) {
				LOG.warn('Subsprite description was not loaded. This should not happen: ' + msDescName);
				return;
			}

			let anchor = msDesc.anchor || { x: 0, y: 0 };
			let msSprite = this._game.make.sprite(anchor.x, anchor.y, msName);
			sprite.addChild(sprite);

			msSprite.anchor = anchor;

			// TODO This should be automatically parsed.
			// Setup the normal data.
			msSprite.scale.setTo(msDesc.scale || 1);
			msSprite.animations.add('bottom.png', ['bottom.png'], 0, true, false);
			msSprite.animations.add('bottom_left.png', ['bottom_left.png'], 0, true, false);
			msSprite.animations.add('left.png', ['left.png'], 0, true, false);
			msSprite.animations.add('left.png', ['left.png'], 0, true, false);
			msSprite.animations.add('top.png', ['top.png'], 0, true, false);
			msSprite.animations.add('top_left.png', ['top_left.png'], 0, true, false);

			// Generate offset information.
			let offsetFileName = this.getOffsetFilename(msName, data.name);
			let offsets = this._game.cache.getJSON(offsetFileName) || {};

			// Prepare the info object.
			let defaultCords = offsets.defaultCords || {
				x: 0,
				y: 0
			};
			let msData = {
				sprite: sprite,
				offsets: offsets.offsets || [],
				name: msName,
				defaultCords: defaultCords
			};

			sprite.name = msName;

			// The whole sprite setup is stupid. We need safty check because we
			// call setSprite() from the parent ctor. Fix this.
			if (!this._multiSprites) {
				this._multiSprites = [];
			}

			this._multiSprites.push(msData);
		}, this);

		// After setting the subsprites we must manually call set
		this._playSubspriteAnimation(this._sprite.animations.currentAnim.name);

		return sprite;
	}

	/**
	 * The type of the entities does now not match the sane check. It must be
	 * corrected.
	 */
	canBuild(data) {
		return data.sprite.type === 'DYNAMIC';
	}

	getOffsetFilename(multispriteName, mainspriteName) {
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