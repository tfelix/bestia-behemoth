
/**
 * Displays a chat text over a entity for a certain time.
 */
export default class ChatTextEntity {
	
	/**
	 * 
	 * @param {Phaser.Sprite}
	 *            origin - This is the sprite over which the chat text will be
	 *            displayed.
	 */
	constructor(game, text, origin) {

		this._game = game;

		this._sprite = this._game.add.text(0, 0, text, ChatTextEntity.STYLE);
		this._sprite.alpha = 0;
		this._sprite.anchor.setTo(0.5);
	
		// Add chat msg.
		origin._sprite.addChild(this._sprite);
	
		this._sprite.position.y = -(origin._sprite.height + ChatTextEntity.Y_OFFSET);
	}
	

	setText(text) {
		this._sprite.text = text;
	}

	appear() {
		var self = this;
		this._sprite.alpha = 1;
		window.setTimeout(function() {
			self._game.world.removeChild(self._sprite);
			self._sprite.destroy();
		}, ChatTextEntity.CHAT_DISPLAY_TIME);
	}
}

// Constant text style.
ChatTextEntity.STYLE = {
	font : "16px Arial",
	fill : "#fff",
	boundsAlignH : "center",
	boundsAlignV : "middle"
};

ChatTextEntity.CHAT_DISPLAY_TIME = 3000;

/**
 * Offset of the chat text to be moved up.
 * 
 * @constant
 */
ChatTextEntity.Y_OFFSET = 65;