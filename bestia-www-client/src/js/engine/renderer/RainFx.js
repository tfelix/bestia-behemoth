
/**
 * The rain controller does the weather rain effect. The intensity controls the
 * amount of rain. Basically the rule of thumb is:
 * <p>
 * intensity = 0, No rain 0 < intensity < 0.3: Light rain. 0.3 < intensity <
 * 0.7: Heavier rain. 0.7 < intensity < 0.9: Storm. intensity > 0.9 : HEAVY
 * Storm.
 * 
 * </p>
 * 
 * @param {Bestia.Engine.EntityCacheManager}
 *            cache
 */
export default class RainFx {
	
	constructor(pubsub, game, groups) {

		this._pubsub = pubsub;
	
		this._game = game;
	
		this._groups = groups;
	
		this._emitter = null;
		
		this._intensity = 0;
	
		/**
		 * 1 if max. brigthness and 0 total darkness.
		 */
		this.intensity = 0;
	
		// pubsub.publish(Bestia.Signal.CHAT_REGISTER_CMD, new
		// Bestia.Chat.Commands.EngineCommand(this));
	}
	
	_addEmitter() {
		// Start rain.
		this._emitter = this._game.add.emitter(this._game.world.centerX, 0, 200);

		this._groups.effects.add(this._emitter);

		this._emitter.width = this._game.width;

		this._emitter.makeParticles('rain');

		this._emitter.minParticleScale = 0.1;
		this._emitter.maxParticleScale = 0.4;

		// emitter.angle = 30; // uncomment to set an angle for the rain.

		this._emitter.setYSpeed(300, 500);
		this._emitter.setXSpeed(-5, 5);

		this._emitter.minRotation = 0;
		this._emitter.maxRotation = 0;
		this._emitter.frequency = 2;

		this._emitter.start(false, 1600, 2, 0);
	}

	/**
	 * Recalculates the emitter settings if intensity has changed.
	 */
	_updateEmitter() {
		if(this._emitter === null) {
			return;
		}
		
		// TODO Regeninstensität berechnen.
		
		if(this._intensity < 0.01) {
			this._emitter.kill();
		} else {
			this._addEmitter();
		}
	}
	
	get intensity() {
		return this._intensity;
	}
	
	set intensity(value) {
		if (value < 0) {
			value = 0;
		}

		if (value > 1) {
			value = 1;
		}

		this._intensity = value;
		this._updateEmitter();
	}
}