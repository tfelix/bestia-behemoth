Bestia.Engine.ChatTextEntity = function(game, text, origin) {

	this._game = game;

	this._sprite = this._game.add.text(0, 0, text, Bestia.Engine.ChatTextEntity.STYLE);
	this._sprite.alpha = 0;
	this._sprite.anchor.setTo(0.5);

	// Add chat msg.
	origin._sprite.addChild(this._sprite);

	this._sprite.position.y = -(origin._sprite.height + Bestia.Engine.ChatTextEntity.Y_OFFSET);
};

Bestia.Engine.ChatTextEntity.prototype.setText = function(text) {
	this._sprite.text = text;
};

Bestia.Engine.ChatTextEntity.prototype.appear = function() {
	var self = this;
	this._sprite.alpha = 1;
	window.setTimeout(function() {
		self._game.world.removeChild(self._sprite);
		self._sprite.destroy();
	}, Bestia.Engine.ChatTextEntity.CHAT_DISPLAY_TIME);
};

// Constant text style.
Bestia.Engine.ChatTextEntity.STYLE = {
	font : "16px Arial",
	fill : "#fff",
	boundsAlignH : "center",
	boundsAlignV : "middle"
};

Bestia.Engine.ChatTextEntity.CHAT_DISPLAY_TIME = 3000;

/**
 * Offset of the chat text to be moved up.
 * 
 * @constant
 */
Bestia.Engine.ChatTextEntity.Y_OFFSET = 65;