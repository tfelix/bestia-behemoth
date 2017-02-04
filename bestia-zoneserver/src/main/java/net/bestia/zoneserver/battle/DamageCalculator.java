package net.bestia.zoneserver.battle;

import java.util.Objects;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackBasedStatus;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.entity.traits.Attackable;

public final class DamageCalculator {

	/**
	 * No instance is needed. The {@link DamageCalculator} can be used entirely
	 * in a static way.
	 */
	private DamageCalculator() {
		// no op.
	}

	/**
	 * Calculates the damage if an attack hits a target. NOTE: No damage will be
	 * applied to the entity. This is in the responsibility of the caller.
	 * 
	 * <p>
	 * Note: The implementation of the damage formula is not COMPLETE!
	 * </p>
	 * 
	 * @param attack
	 * @param user
	 * @param target
	 * @return The damage the entity would take.
	 */
	public static Damage calculate(Attack attack, Attackable user, Attackable target) {
		Objects.requireNonNull(attack);
		Objects.requireNonNull(user);
		Objects.requireNonNull(target);

		final float atkV;
		final float defV;

		if (attack.getBasedStatus() == AttackBasedStatus.NORMAL) {
			atkV = user.getStatusPoints().getStrength();
			defV = target.getStatusPoints().getDefense();
		} else {
			atkV = user.getStatusPoints().getIntelligence();
			defV = target.getStatusPoints().getMagicDefense();
		}

		// Calculate base damage.
		float dmg = 2.0f * user.getLevel() + 10 / 250 * (atkV / defV) * attack.getStrength() + 2;

		// Calculate all the needed modifier.

		// same type as attack.
		final float stabMod = (attack.getElement() == user.getElement()) ? 1.3f : 1.0f;
		final float sizeMod = 1.0f;

		final float mods = stabMod * sizeMod;

		dmg *= mods;

		final Damage damage = Damage.fromDamage((int) dmg);

		return damage;
	}

}
