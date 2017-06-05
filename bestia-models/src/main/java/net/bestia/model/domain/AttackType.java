package net.bestia.model.domain;

public enum AttackType {

	/**
	 * A melee attack is performed using the strength of the entity/bestia to
	 * perform the attack.
	 */
	MELEE,

	/**
	 * A ranged attack is some sort of projectile hurled towards the target.
	 * This might be either magic ranged (fireballs, lightning etc) or
	 * conventional projectils like arrows.
	 */
	RANGED
}
