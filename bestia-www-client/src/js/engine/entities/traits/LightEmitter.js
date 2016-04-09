
/**
 * This trait will extend an entity in a way that it will emit a certain 
 */
Bestia.Engine.Traits.LightEmitter = function(entity) {
	
	/**
	 * How fast the flicker of the fire will be.
	 */
	this._frequency = 1;
	
	this._min = 0.5;
	this._max = 1.0;
	
	this._bright = 100;
	this._color = 

	entity.lightEmitter = this;
};

Bestia.Engine.Traits.LightEmitter.hasEmitter = function(entity) {
	return entity['lightEmitter'] !== undefined;
};

Bestia.Engine.Traits.LightEmitter.prototype.drawLight = function(shadowMap) {
	
	var gradient = shadowMap.ctx.createRadialGradient(700, 260, 100 * 0.75, 700, 260, 200);
	gradient.addColorStop(0, 'rgba(255, 255, 255, 1.0)');
	gradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)');

	shadowMap.context.beginPath();
	shadowMap.context.fillStyle = gradient;
	shadowMap.context.arc(700, 260, 200, 0, Math.PI * 2, false);
	shadowMap.context.fill();
	
};