package net.bestia.zoneserver.battle;

import net.bestia.model.domain.Attack;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.proxy.Entity;

public class DamageCalculator {

	/**
	 * No instance is needed. The {@link DamageCalculator} can be used entirely
	 * in a static way.
	 */
	private DamageCalculator() {
		// no op.
	}

	/**
	 * Calculates the damage if an attack hits a target. NOTE: No damage will be
	 * applied to the entity. This is in the reponsibility of the caller.
	 * 
	 * @param attack
	 * @param user
	 * @param target
	 * @return The damage the entity would take.
	 */
	public static Damage calculateDamage(Attack attack, Entity user, Entity target) {
		final Damage dmg = Damage.getHit("1234", 1);
		return dmg;
	}

}
