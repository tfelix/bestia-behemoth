package net.bestia.model.entity;

/**
 * Different kinds of sprite sheets.
 *
 */
public enum SpriteType {
	/**
	 * Generic still image from the assets without any animations.
	 */
	SINGLE,

	/**
	 * The sprite, its animations etc is described via the bestia pack format.
	 * This is the usual format for mob sprites. They are described inside this
	 * pack.
	 */
	PACK,

	/**
	 * It is an item sprite. (Basically a still image but in a different
	 * folder).
	 */
	ITEM,

	/**
	 * This sprite is dynamically put together. It is treated like a pack sprite
	 * for the beginning but instead of using the multi sprite information from
	 * the pack the info from the server is preferred.
	 */
	MULTI_DYNAMIC
}
