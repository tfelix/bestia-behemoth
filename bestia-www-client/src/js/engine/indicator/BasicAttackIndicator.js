
import Indicator from './Indicator.js';
import Signal from '../../io/Signal.js';

/**
 * This indicator will manage the the snapping and colorization effect of an
 * hover over an attackable entity sprite.
 */
export default class BasicAttackIndicator extends Indicator {
	
	constructor(manager) {
		super(manager);
		
		/**
		 * Max range of the basic attack.
		 * 
		 * @constant
		 */
		this.RANGE = 1;

		this._marker = null;

		this._targetSprite = null;
		this._targetEntity = null;

		// Listen for activation signal.
		this._ctx.pubsub.subscribe(Signal.ENGINE_REQUEST_INDICATOR, this._handleIndicator.bind(this));
	}
	
	activate() {
		this._ctx.game.input.onDown.add(this._onClick, this);
		this._marker.reset();
		this._targetSprite.addChild(this._marker);
	}

	deactivate() {
		this._ctx.game.input.onDown.remove(this._onClick, this);
		this._marker.kill();
	}

	/**
	 * Preload all needed assets.
	 */
	load() {
		this._ctx.game.load.image('cursor_atk', this._ctx.url.getSpriteUrl('cursor_atk'));
	}

	/**
	 * Preload all needed assets.
	 */
	create() {
		this._marker = this._ctx.game.make.sprite(0, 0, 'cursor_atk');
		this._marker.anchor.setTo(0.5, 0.5);
		this._marker.angle = 0;
	}

	_handleIndicator(_, data) {
		
		if (data.handle === 'basic_attack_over') {
			this._targetSprite = data.entity.sprite;
			this._targetEntity = data.entity;
			
			// Do some wiring. If the sprite dies we need to give up controls.
			this._targetSprite.events.onDestroy.add(function(){
				this._manager.dismissActive();
			}, this);
			
			this._requestActive();
		} else if (data.handle === 'basic_attack_out') {
			this._manager.dismissActive();
		}
	}

	_onClick(pointer) {

		// If we are close enough we send a attack request to the server, if we are
		// too far away we will move towards the target.
		if (pointer.button !== Phaser.Mouse.LEFT_BUTTON) {
			return;
		}

		// Publish the cast information.
		var player = this._ctx.getPlayerEntity();
		var pointerCords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
		
		var d = Bestia.Engine.World.getDistance(player.position, pointerCords);
		
		if(d > this.RANGE) {
			// Move to target.
			var path = this._ctx.zone.findPath(player.position, pointerCords).nodes;

			// Path not found.
			if (path.length === 0) {
				return;
			}

			path = path.reverse();
			var msg = new Bestia.Message.BestiaMove(player.playerBestiaId, path, this._ctx.playerBestia.walkspeed());
			this._ctx.pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);

			// Start movement locally as well.
			player.moveTo(path, this._ctx.playerBestia.walkspeed());
		} else {
			// Attack.
			var msg = new Bestia.Message.BasicMeleeAttackUse(this._targetEntity.uuid);
			this._ctx.pubsub.publish(Bestia.Signal.IO_SEND_MESSAGE, msg);
		}
	}	
}