package net.bestia.model.misc;

/**
 * Different kinds of sprite sheets.
 *
 */
public enum SpriteType {
	/**
	 * Generic still image from the assets.
	 */
	SINGLE,

	/**
	 * The sprite, its animations etc is described via the bestia pack
	 * format.
	 */
	PACK, 
	
	/**
	 * It is an item sprite.
	 */
	ITEM, 
	
	/**
	 * It is a multisprite. Basically its multiple pack sprites.
	 */
	MULTI
}
