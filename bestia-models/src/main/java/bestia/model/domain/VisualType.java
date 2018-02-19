package bestia.model.domain;

/**
 * Entities are visualized via visuals. These visuals can either be a simple
 * sprite, a sprite build from multiple sprites or even a complex json
 * description file which tells the engine how to display itself.
 *
 */
public enum VisualType {
	/**
	 * Generic still image from the assets without any animations.
	 */
	SINGLE,

	/**
	 * The sprite, its animations etc is described via the bestia pack format.
	 * This is the usual format for mob sprites. They are described inside this
	 * pack. It is actual quite the default sprite format.
	 */
	PACK,

	/**
	 * It is an item sprite. (Basically a still image but located in a different
	 * data folder).
	 */
	ITEM,

	/**
	 * This sprite is dynamically put together. It is treated like a pack sprite
	 * for the beginning but instead of using the multi sprite information from
	 * the pack the info from the server is preferred. The player sprites with
	 * different hairstyles are one example for this type of visual.
	 */
	DYNAMIC,

	/**
	 * Visualization is done via an descriptive json file and is rendered by combining
	 * different components.
	 */
	OBJECT
}
