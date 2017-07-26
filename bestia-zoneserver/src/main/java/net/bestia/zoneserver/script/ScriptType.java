package net.bestia.zoneserver.script;

/**
 * Denotes the different type of scripts used for the bestia game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public enum ScriptType {

	/**
	 * An item script.
	 */
	ITEM,

	/**
	 * An attack script.
	 */
	ATTACK,

	/**
	 * An status effect script.
	 */
	STATUS_EFFECT,

	/**
	 * No special script type.
	 */
	NONE,

	/**
	 * Scripts found on entities placed on the map.
	 */
	MAP

}
