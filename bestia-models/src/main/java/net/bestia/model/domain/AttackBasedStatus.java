package net.bestia.model.domain;

/**
 * Specifies on which status value an attack is based.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public enum AttackBasedStatus {
	/**
	 * Attack is based on special attack stat.
	 */
	SPECIAL, 
	/**
	 * Attack is based on normal attack stat.
	 */
	NORMAL,
	/**
	 * Attacks which do a special calculation and deal no real damage (it use none of the battle based stats)
	 */
	NO_DAMAGE
}
