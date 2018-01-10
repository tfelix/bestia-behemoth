import * as Phaser from 'phaser';
import Indicator from './Indicator.js';
import Signal from '../../io/Signal.js';
import WorldHelper from '../map/WorldHelper.js';
import Message from '../../io/messages/Message.js';
import LOG from '../../util/Log';

/**
 * This indicator will manage the the snapping and colorization effect of an
 * hover over an attackable entity sprite.
 */
export default class BasicAttackIndicator extends Indicator {
	
	constructor(manager, engineContext) {
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
		this._ctx = engineContext;

		// Listen for activation signal.
		engineContext.pubsub.subscribe(Signal.ENGINE_REQUEST_INDICATOR, this._handleIndicator.bind(this));
	}
	
	activate() {
		this._ctx.game.input.onDown.add(this._onClick, this);
		this._marker.reset();
		this._targetSprite.addChild(this._marker);
		this._targetSprite.bringToTop();
	}

	deactivate() {
		this._ctx.game.input.onDown.remove(this._onClick, this);
		this._marker.kill();
	}

	/**
	 * Preload all needed assets.
	 */
	load(loader) {
		loader.image('cursor_atk', this._ctx.url.getIndicatorUrl('cursor_atk'));
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
			this._targetSprite = data.entity.getRootVisual();
			this._targetEntity = data.entity;
			
			// Do some wiring. If the sprite dies we need to give up controls.
			this._targetSprite.events.onDestroy.add(function(){
				this.deactivate();
				this._manager.dismissActive();
			}, this);
			
			this.activate();
			this._manager.requestActive(this);
			
		} else if (data.handle === 'basic_attack_out') {
			this.deactivate();
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
		var player = this._ctx.playerEntity;
		var pointerCords = WorldHelper.getTileXY(pointer.worldX, pointer.worldY);
		
		var d = WorldHelper.getDistance(player.getPosition(), pointerCords);
		
		if(d > this.RANGE) {
			LOG.warn('Out of range.');
		} else {
			// Attack.
			let msg = new Message.BasicMeleeAttackUse(this._targetEntity.id);
			this._ctx.pubsub.publish(Signal.IO_SEND_MESSAGE, msg);
		}
	}	
}