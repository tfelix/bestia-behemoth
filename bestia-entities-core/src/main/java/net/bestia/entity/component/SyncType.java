package net.bestia.entity.component;

/**
 * Hint for the intercepter how to sync this components.
 */
public enum SyncType {

	/**
	 * All active clients in sight will be updated. This should be done if its a
	 * public visible component like the sprite or animation info or position.
	 */
	ALL,

	/**
	 * Only the client itself is informed. This is needed if the component only
	 * contains client private data like status values.
	 */
	OWNER

}
