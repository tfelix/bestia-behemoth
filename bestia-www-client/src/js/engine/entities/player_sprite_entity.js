/**
 * This entity is a bit different. It supports a head sprite which can be moved
 * async to the body sprite to give a certain more realistic look to the players
 * then the mobs.
 * 
 * @constructor
 * @this {Bestia.Engine.PlayerSpriteEntity}
 * @param {number}
 *            playerBestiaId The ID of the player bestia represented by this
 *            sprite.
 */
Bestia.Engine.PlayerSpriteEntity = function(game, uuid, x, y, playerBestiaId) {
	Bestia.Engine.SpriteEntity.call(this, game, uuid, x, y, playerBestiaId);

	/**
	 * 
	 * @private
	 * @property {number}
	 */
	this._playerBestiaId = playerBestiaId;

};

Bestia.Engine.PlayerSpriteEntity.prototype = Object.create(Bestia.Engine.SpriteEntity.prototype);
Bestia.Engine.PlayerSpriteEntity.prototype.constructor = Bestia.Engine.PlayerSpriteEntity;
