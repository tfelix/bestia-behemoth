/*global Phaser */

import Indicator from './Indicator.js';
import Message from '../../io/messages/Message.js';
import WorldHelper from '../map/WorldHelper.js';
import { engineContext, entityCache, pathfinder } from '../EngineData';
import { entityAddMovement } from '../entities/traits/MovementTrait';
import Signal from '../../io/Signal';

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
export default class MoveIndicator extends Indicator {
	constructor(manager) {
		super(manager);

		this._effect = null;

		this._pubsub = engineContext.pubsub;
		this._game = engineContext.game;

		this.playerBestia = null;
		this._pubsub.subscribe(Signal.BESTIA_SELECTED, function (_, bestia) {
			this._playerBestia = bestia;
		}, this);
	}

	_onPathFound(path) {
		// Callback function.

		path = path || [];

		if (path.length === 0) {
			return;
		}

		// Remove first element since its the current position.
		path.shift();

		var pbid = this._playerBestia.playerBestiaId();
		var eid = this._playerBestia.entityId();
		var speed = this._playerBestia.statusBasedValues.walkspeed();

		var msg = new Message.EntityMove(pbid,
			eid,
			path,
			speed);
		this._pubsub.send(msg);

		// Start movement locally as well.
		var entity = entityCache.getEntity(eid);
		entityAddMovement(entity, path, speed, 0);
	}

	_onClick(pointer) {

		// No player no movement.
		if (!this._playerBestia) {
			return;
		}

		// Only left button.
		if (pointer.button !== Phaser.Mouse.LEFT_BUTTON) {
			return;
		}

		// Display fx.
		this._effect.alpha = 1;
		this._game.add.tween(this._effect).to({ alpha: 0 }, 500, Phaser.Easing.Cubic.Out, true);

		var goal = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);

		// Start the path calculation
		pathfinder.findPath(this._playerBestia.position(), goal, this._onPathFound.bind(this));
	}

	/**
	 * Override an create all needed game objects here.
	 */
	load() {

		this._game.load.spritesheet('cursor', engineContext.url.getIndicatorUrl('cursor'), 32, 32);
	}

	/**
	 * Override an create all needed game objects here.
	 */
	create() {
		this._marker = this._game.make.sprite(0, 0, 'cursor');
		this._marker.name = 'cursor';
		this._marker.animations.add('blink');
		this._marker.animations.play('blink', 1, true);

		var graphics = this._game.make.graphics(0, 0);
		graphics.beginFill(0x42D65D);
		graphics.drawRect(0, 0, 32, 32);
		graphics.endFill();
		graphics.alpha = 0;
		this._effect = graphics;
		this._marker.addChild(this._effect);
	}
}
