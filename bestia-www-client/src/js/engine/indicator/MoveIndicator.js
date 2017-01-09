/*global Phaser */

import Indicator from './Indicator.js';
import Message from '../../io/messages/Message.js';
import WorldHelper from '../map/WorldHelper.js';

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
export default class MoveIndicator extends Indicator {
	constructor(manager) {
		super(manager);
		
		this._effect = null;
	}
	
	_onClick(pointer) {
		
		// Only left button.
		if(pointer.button !== Phaser.Mouse.LEFT_BUTTON) {
			return;
		}
		
		// Display fx.
		this._effect.alpha = 1;
		this._ctx.game.add.tween(this._effect).to({alpha: 0}, 500, Phaser.Easing.Cubic.Out, true);
		

		var player = this._ctx.playerBestia;
		
		if(player === null) {
			return;
		}

		var goal = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);
		
		// Callback function.
		this._ctx.etc.pathfinder.setCallbackFunction(function(path) {
			path = path || [];
			
			if(path.length === 0) {
				return;
			}
			
			// Remove first element since its the current position.
			path.shift();
			
			var msg = new Message.EntityMove(player.playerBestiaId, player.entityId(), path, player.walkspeed());
			this._ctx.pubsub.send(msg);

			// Start movement locally as well.
			this._ctx.playerEntity.moveTo(path, this._ctx.playerBestia.walkspeed());
		}.bind(this));
		
		// Start the path calculation
		this._ctx.etc.pathfinder.preparePathCalculation([player.posX(), player.posY()], [goal.x, goal.y]);
		this._ctx.etc.pathfinder.calculatePath();
	}

	/**
	 * Override an create all needed game objects here.
	 */
	load() {
		this._ctx.game.load.spritesheet('cursor', this._ctx.url.getIndicatorUrl('cursor'), 32, 32);
	}

	/**
	 * Override an create all needed game objects here.
	 */
	create() {
		this._marker = this._ctx.game.make.sprite(0, 0, 'cursor');
		this._marker.name = 'cursor';
		this._marker.animations.add('blink');
		this._marker.animations.play('blink', 1, true);
		
		var graphics = this._ctx.game.make.graphics(0, 0);
		graphics.beginFill(0x42D65D);
		graphics.drawRect(0, 0, 32, 32);
		graphics.endFill();
		graphics.alpha = 0;
		this._effect = graphics;
		this._marker.addChild(this._effect);
	}
}
