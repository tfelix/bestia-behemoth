/**
 * The manager is responsible for switching the indicator depending on the needs
 * of the engine. It listens to various events (usage of an item for example)
 * and in case of this will switch the indicator. This indicator then gets the
 * control of the inputs and must react accordingly.
 */
Bestia.Engine.IndicatorManager = function() {
	
	this._indicators = [];
	
	this.pubsub = null;
	
	this.game = null;
	
	this.engine = null;
	
	this._active = null;

	this._moveIndicator = new Bestia.Engine.Indicator.Move(this);
	this._indicators.push(this._moveIndicator);
	this._indicators.push(new Bestia.Engine.Indicator.ItemCast(this));
	this._indicators.push(new Bestia.Engine.Indicator.AttackCast(this));
};

Bestia.Engine.IndicatorManager.prototype.showStandardIndicator = function() {
	this.requestActive(this._moveIndicator);
};

/**
 * The indicator can request to get displayed.
 */
Bestia.Engine.IndicatorManager.prototype.loadAssets = function(loader) {
	this._indicators.forEach(function(x){
		x.loadAssets(loader);
	}, this);
};

/**
 * The indicator can request to get displayed.
 */
Bestia.Engine.IndicatorManager.prototype.requestActive = function(indicator) {
	if(this._active !== null) {
		this._active.deactivate();
	}
	
	this._active = indicator;
	this._activa.activate();
};
