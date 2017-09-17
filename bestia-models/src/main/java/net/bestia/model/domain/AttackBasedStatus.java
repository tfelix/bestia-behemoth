package net.bestia.model.domain;

/**
 * Specifies on which status value an attack is based.
 * 
 * @author Thomas Felix
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
	 * Attacks which do a special calculation and deal no real damage (it uses
	 * none of the battle based stats). All effects or damage is done via
	 * scripts which are deployed upon usage of this attack.
	 */
	NO_DAMAGE
}
