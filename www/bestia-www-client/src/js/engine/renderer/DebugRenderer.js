import Renderer from './Renderer';
import { engineContext } from '../EngineData';

const TXT_STYLE = Object.freeze({
	font: '15px Arial',
	fill: '#ffffff',
	boundsAlignH: 'center',
	boundsAlignV: 'middle'
});

/**
 * Enables the entity id rendering.
 * @param {boolean} isEnabled Flag if the entity id rendering is enabled or not.
 */
export function setDebugEntityIdDisplay(isEnabled) {

	engineContext.data.showEntityId = isEnabled;

	if (!isEnabled) {
		/*entityCache.getAllEntities().forEach(function (entity) {
			var sprite = spriteCache.getSprite(entity.eid);

			if (sprite == null || sprite._debugEidTxt) {
				return;
			}

			sprite._debugEidTxt.destroy();
			delete sprite._debugEidTxt;

		}, this);*/
	}
}

export class DebugRenderer extends Renderer {

	constructor(game) {
		super();

		this._game = game;
	}

	get name() {
		return 'debug';
	}

	isDirty() {
		return engineContext.data.hasOwnProperty('showEntityId');
	}

	clear() {
		// no op
	}

    /**
     * Iterates through every entity and checks if there is render work to be done
     * to display stuff attached to this entity.
     */
	update() {
		/*
		entityCache.getAllEntities().forEach(function (entity) {
			var sprite = spriteCache.getSprite(entity.eid);

			if (sprite == null || sprite._debugEidTxt) {
				return;
			}

			let eidSprite = this._game.add.text(0, 0, entity.eid, TXT_STYLE);
			eidSprite.anchor.set(0.5, 0);
			eidSprite.setScaleMinMax(1, 1);

			sprite._debugEidTxt = eidSprite;
			sprite.addChild(eidSprite);

		}, this);*/
	}
}