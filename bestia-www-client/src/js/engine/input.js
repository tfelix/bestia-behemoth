Bestia.Input = {};

/**
 * The InputController manages the the player input. The current input is
 * received via an input receiver.
 * 
 * @class Bestia.InputConfig
 * @contructor
 */
Bestia.InputConfig = function(pubsub, config) {

	//var self = this;

	/**
	 * @property {Bestia.Config} config - Holds a reference to the central
	 *           config object for the bestia game. So User options can be read
	 *           an used.
	 */
	this.config = config;

	/**
	 * @property {Bestia.PubSub} pubsub - Holds a reference to the bestia
	 *           publish/subscribe interface allowing game engine objects to
	 *           subscribe to events.
	 */
	this.pubsub = pubsub;

};

/**
 * 
 * @return Model
 */
Bestia.InputConfig.prototype.getModel = function() {

};

/**
 * 
 * @constant
 */
Bestia.Input.Event = {
	ATTACK_1_USE : 101,
	ATTACK_2_USE : 102,
	ATTACK_3_USE : 103,
	ATTACK_4_USE : 104,
	ATTACK_5_USE : 105,
	ITEM_SHORT_1_USE : 201,
	ITEM_SHORT_2_USE : 202,
	ITEM_SHORT_3_USE : 203,
	ITEM_SHORT_4_USE : 204,
	ITEM_SHORT_5_USE : 205
};

/**
 * The keyboard receiver takes keycodes send via a keyboard, translates them to
 * Bestia.Input.Actions and sends them out via a pubsub event to all interested
 * parties.
 * 
 * @class
 * @constructor
 */
Bestia.Input.KeyboardReceiver = function(pubsub, model) {
	
	var self = this;
	
	this._model = model;
	
	this._pubsub = pubsub;
	
	/**
	 * Handler will translate between keycode and event via the keymodel.
	 */
	this._handler = function(event) {
		var action = self._model[event.keyCode];
		
		if(action === undefined) {
			return;
		}
		
		self._pubsub.publish('input', {action: action});
	};

	// Subscribe to keypress events.
	$(document).on("keydown", this._handler);
};

/**
 * In order to replace a receiver, remove() must be called. The call will
 * unsubscribe him from keyboard events and it will stop to propagate events
 * into the bestia system for pressed keys.
 * 
 * @method Bestia.Input.KeyboardReceiver#remove
 */
Bestia.Input.KeyboardReceiver.prototype.remove = function() {
	// Subscribe to keypress events.
	$(document).off("keydown", this._handler);
};