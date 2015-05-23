package net.bestia.model;

/**
 * Specifies on which status value an attack is based.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public enum AttackBasedStatus {
	SPECIAL,
	NORMAL,
	/**
	 * Attacks which do a special calculation and deal no real damage 
	 *(it use none of the battle based stats)	
	 */
	NO_DAMAGE
}
