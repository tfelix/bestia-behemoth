package net.bestia.messages.entity;

/**
 * Certain actions can not be told to the client via an entity message
 * alone. Some actions like a disappearing entity must be send via this
 * special action marker.
 *
 */
public enum EntityAction {
	/**
	 * Object just spawned. 
	 */
	APPEAR,
	
	/**
	 * Entity has died.
	 */
	DIE,
	
	/**
	 * Entity has vanished and is basically now invisible.
	 */
	VANISH
}