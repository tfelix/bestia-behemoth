import Builder from './Builder.js';
import ItemEntity from '../ItemEntity.js';
import { engineContext } from '../../EngineData';

/**
 * Responsible for building item entities displayed on the map.
 */
export default class ItemBuilder extends Builder {
	constructor(factory) {
		super(factory);


		// Register with factory.
		this.type = 'item';
		this.version = 1;

		this._loader = engineContext.loader;
		this._game = engineContext.game;
	}

	build(data) {
		if (data.onlyLoad) {
			return null;
		}

		var entity;

		if (this._loader.has(data.s, 'item')) {
			entity = new ItemEntity(data.uuid, data.x, data.y, data.s);
		} else {
			entity = new ItemEntity(data.uuid, data.x, data.y);

			this._demandLoader.loadItemSprite(data.s, function () {

				entity.setTexture(data.s);

			}.bind(this));
		}

		if (data.a === 'APPEAR') {
			entity.appear();
		} else {
			entity.show();
		}

		return entity;
	}

	canBuild(data) {
		return data.type === this.type && data.version === this.version;
	}
}