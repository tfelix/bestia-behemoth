package net.bestia.zoneserver.script;

import net.bestia.zoneserver.script.env.ScriptEnv;

/**
 * Denotes the different type of scripts used for the bestia game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @deprecated The script type should not be needed anymore. Script execution
 *             are now done via {@link ScriptEnv}s.
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
