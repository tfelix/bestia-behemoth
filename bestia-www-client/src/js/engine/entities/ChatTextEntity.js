
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

		let box = this._game.add.graphics(0, 0);
		box.beginFill(0xFF3300);
		box.alpha = 0;
		
		let textSprite = this._game.add.text(5, 0, text, ChatTextEntity.STYLE);
		
		box.drawRect(0, 0, textSprite.width + 10, textSprite.height);
		box.anchor.setTo(0.5);
		box.addChild(textSprite);
		
		box.position.y = -Math.round(origin._sprite.height + ChatTextEntity.Y_OFFSET);
		box.position.x = Math.round(textSprite.x);
	
		// Add chat msg.
		origin._sprite.addChild(box);
		this._sprite = box;
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
	font : '26px Arial',
	fill : '#fff',
	boundsAlignH : 'center',
	boundsAlignV : 'middle'
};

ChatTextEntity.CHAT_DISPLAY_TIME = 3000;

/**
 * Offset of the chat text to be moved up.
 * 
 * @constant
 */
ChatTextEntity.Y_OFFSET = 65;