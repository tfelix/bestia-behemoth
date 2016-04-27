/**
 * The manager is responsible for switching the indicator depending on the needs
 * of the engine. It listens to various events (usage of an item for example)
 * and in case of this will switch the indicator. This indicator then gets the
 * control of the inputs and must react accordingly.
 * <p>
 * The manager does also listen to change requests from the outside. So it is
 * possible to react upon hover effects over sprites for example.
 * </p>
 */
Bestia.Engine.IndicatorManager = function(ctx) {

	this._indicators = [];

	/**
	 * We will buffer the calls to the indicator in order to re-display them.
	 */
	this._indicatorStack = [];

	this._active = null;

	this.ctx = ctx;

	this._moveIndicator = new Bestia.Engine.Indicator.Move(this);
	this._indicators.push(this._moveIndicator);
	this._indicators.push(new Bestia.Engine.Indicator.ItemCast(this));
	this._indicators.push(new Bestia.Engine.Indicator.BasicAttack(this));
};

/**
 * Shows the default pointer. It will also clear the pointer stack.
 */
Bestia.Engine.IndicatorManager.prototype.showDefault = function() {
	this.requestActive(this._moveIndicator);
	this._indicatorStack = [];
};

Bestia.Engine.IndicatorManager.prototype.load = function() {
	this._indicators.forEach(function(x) {
		x.load();
	}, this);
};

Bestia.Engine.IndicatorManager.prototype.create = function() {
	this._indicators.forEach(function(x) {
		x.create();
	}, this);
};

/**
 * The indicator can request to get displayed.
 */
Bestia.Engine.IndicatorManager.prototype.requestActive = function(indicator) {
	if (this._active !== null) {
		// Ask the active pointer if he allows to be overwritten by the new
		// indicator.
		if (!this._active.allowOverwrite(indicator)) {
			return;
		}

		this._indicatorStack.push(this._active);
		this._active.deactivate();
	}

	this._active = indicator;
	this._active.activate();
};

/**
 * No pushing to the indicator stack will occure when using this method.
 * Otherwise its the same as requestActive.
 */
Bestia.Engine.IndicatorManager.prototype._setActive = function(indicator) {
	if (this._active !== null) {
		// Ask the active pointer if he allows to be overwritten by the new
		// indicator.
		if (!this._active.allowOverwrite(indicator)) {
			return;
		}
		this._active.deactivate();
	}
	this._active = indicator;
	this._active.activate();
};

/**
 * The indicator can request to get dismissed. It will be replaced with last
 * indicator.
 */
Bestia.Engine.IndicatorManager.prototype.dismissActive = function() {
	if (this._indicatorStack.length === 0) {
		this._active = this._moveIndicator;
	} else {
		var indi = this._indicatorStack.pop();
		this._setActive(indi);
	}
};
