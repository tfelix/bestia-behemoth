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
	VANISH, 
	
	/**
	 * No particular action is needed. Just an update message regarding this entity.
	 * @Deprecated Dont use this anymore. Use component updates instead.
	 *
	 */
	@Deprecated
	UPDATE
}