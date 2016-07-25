import EngineCommand from '../../chat/commands/EngineCommand.js';
import Signal from '../../io/Signal.js';

/**
 * The brightness controller listens to messages containing the global
 * brightness. The fx controller will prepare a shadow map containing the
 * brightness and if there is a shadow it will start to calculate the dynamic
 * lightning effects.
 * 
 * @param {Bestia.Engine.EntityCacheManager}
 *            cache
 */
export default class BrightnessFx {
	
	constructor(ctx) {

		this._pubsub = ctx.pubsub;
	
		this._game = ctx.game;
		
		this._ctx = ctx;
	
		/**
		 * 1 if max. brigthness and 0 total darkness.
		 */
		this.brightness = 1;
		
		this._pubsub.publish(Signal.CHAT_REGISTER_CMD, new EngineCommand(this));
	}
	
	destroy() {
		// no op.
	}

	create() {
		// We need two layers. One for shadow, one for color informations. Both must
		// be updated by the objects.
		this.shadowMap = this._ctx.game.add.bitmapData(this._game.width, this._game.height);
		this.shadowImg = this._ctx.game.add.image(0, 0, this.shadowMap);

		this.shadowImg.blendMode = Phaser.blendModes.MULTIPLY;

		this.shadowMap.ctx.fillStyle = '#000000';
		this.shadowMap.ctx.beginPath();
		this.shadowMap.ctx.fillRect(0, 0, this._game.width, this._game.height);
		this.shadowMap.ctx.closePath();

		this.shadowMap.dirty = true;
		
		this._ctx.groups.overlay.add(this.shadowImg);
	}

	update() {
		
		this.shadowImg.alpha = this._currentBrightness;
		
		if(this._currentBrightness >= 0.99) {
			return;
		}
		
		// Gather all entities in sight with a light emitting trait and let them add to the shadow map.
		//var entities = [];
		
		// Clear the map.
		this.shadowMap.ctx.fillStyle = '#000000';
		this.shadowMap.ctx.beginPath();
		this.shadowMap.ctx.fillRect(0, 0, this._game.width, this._game.height);
		this.shadowMap.ctx.closePath();
		
		this.shadowMap.dirty = true;
	}
	
	get brightness() {
		return this._currentBrightness;
	}
	
	set brightness(value) {
		if(value < 0) {
			value = 0;
		}
		
		if(value > 1) {
			value = 1;
		}

		this._currentBrightness = 1 - value;
	}
}
