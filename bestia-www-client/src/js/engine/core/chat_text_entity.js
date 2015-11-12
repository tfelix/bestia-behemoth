Bestia.Engine.ChatTextEntity = function(game, text) {

	this._game = game;
	
	this._sprite = this._game.add.text(0, 0, text, Bestia.Engine.EntityText.STYLE);
	
	this._sprite.anchor.setTo(0.5);
};

Bestia.Engine.ChatTextEntity.prototype.setText = function(text) {
	this._sprite.text = text;
};

Bestia.Engine.ChatTextEntity.prototype.appear = function() {
	var self = this;
	this._sprite.alpha = 1;
	window.setTimeout(Bestia.Engine.EntityText.CHAT_DISPLAY_TIME, function(){
		self._sprite.destroy();
	});
};


//Constant text style.
Bestia.Engine.ChatTextEntity.STYLE = {
	font : "bold 32px Arial",
	fill : "#fff",
	boundsAlignH : "center",
	boundsAlignV : "middle"
};

Bestia.Engine.ChatTextEntity.CHAT_DISPLAY_TIME = 3000;