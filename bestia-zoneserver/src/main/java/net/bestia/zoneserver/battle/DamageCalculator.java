package net.bestia.zoneserver.battle;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.model.battle.Damage;
import net.bestia.model.battle.Damage.DamageType;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackType;
import net.bestia.model.domain.StatusPoints;

public class DamageCalculator {

	private static final Logger LOG = LoggerFactory.getLogger(DamageCalculator.class);

	private Random rand = ThreadLocalRandom.current();

	/**
	 * Calculates the damage if an attack hits a target entity. This calculates
	 * damage as long as the target entity is able to participate in the damage
	 * system. This means it must have a status component attached.
	 * 
	 * If the target entity does not fulfill the requirements of getting a
	 * damage calculated it will throw {@link IllegalArgumentException}.
	 * 
	 * NOTE: No damage will be applied to the entity. This is in the
	 * responsibility of the caller.
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
	public Damage calculate(BattleContext ctx, DamageVariables dmgVars) {

		// Use the capped values.
		dmgVars = dmgVars.getCappedValues();

		final int baseAtk = getBaseAtk();
		final float atkMod = getAttackMod(ctx.getUsedAttack(), dmgVars);
		final float hardDefMod = getHardDefMod(ctx, dmgVars);
		final float critMod = getCritMod(ctx, dmgVars);
		final int softDef = getSoftDef(ctx, dmgVars);

		final int damage = (int) Math.floor((baseAtk * atkMod * hardDefMod * critMod) - softDef);
	}

	private int getSoftDef(BattleContext ctx, DamageVariables dmgVars) {

		final AttackType type = ctx.getUsedAttack().getType();

		final StatusPoints defPoints = ctx.getDefenderStatusPoints();

		// TODO das hier muss noch ausgelesen werden. :/
		final int level = 1;
		float softDef = 0;

		if (type == AttackType.MELEE_MAGIC || type == AttackType.RANGED_MAGIC) {
			// Magic attack.
			softDef = level / 2f + defPoints.getVitality() + defPoints.getStrength() / 3f;
		} else {
			// physical attack.
			softDef = level / 2f + defPoints.getVitality() + defPoints.getWillpower() / 4f
					+ defPoints.getIntelligence() / 5f;
		}

		return (int) Math.min(0, softDef);
	}

	private float getCritMod(BattleContext ctx, DamageVariables dmgVars) {
		
		final float baseCritRate = toMod(ctx.getAttackerStatusBased().getCriticalHitrate());
		
		if(rand.nextFloat() > baseCritRate) {
			// Scored critical hit.
			return 1.4f * dmgVars.getBonusCritMod();
		} else {
			// no crit.
			return 1f;
		}
	}

	private float getHardDefMod(BattleContext ctx, DamageVariables dmgVars) {

		final AttackType type = ctx.getUsedAttack().getType();
		float defMod = 0;

		if (type == AttackType.MELEE_MAGIC || type == AttackType.RANGED_MAGIC) {
			final int def = ctx.getAttackerStatusPoints().getDefense();
			defMod = 1 - (def + dmgVars.getArmorMod());
		} else {
			final int def = ctx.getAttackerStatusPoints().getMagicDefense();
			defMod = 1 - (def + dmgVars.getMagicResist());
		}

		return between(defMod, 0.05f, 1);
	}

	/**
	 * Returns the attack mod used for the type of attack.
	 * 
	 * @param attack
	 *            The attack used.
	 * @param dmgVars
	 *            The damage vars.
	 * @return The current attack mod.
	 */
	private float getAttackMod(Attack attack, DamageVariables dmgVars) {
		switch (attack.getType()) {
		case MELEE_MAGIC:
			return dmgVars.getBonusMagicMeleeDamage();
		case MELEE_PHYSICAL:
			return dmgVars.getBonusPhysicalMeleeDamage();
		case RANGED_PHYSICAL:
			return dmgVars.getBonusPhysicalRangedDamage();
		case RANGED_MAGIC:
			return dmgVars.getBonusMagicRangedDamage();
		default:
			LOG.error("Attack type of attack {} is unknown and returns now bonus value.", attack);
			return 0;
		}
	}

	private int getBaseAtk() {
		// TODO Auto-generated method stub
		return 0;
	}

	private float between(float x, float min, float max) {
		return Math.max(Math.max(x, min), min);
	}
	
	private float toMod(int x) {
		return x / 100f;
	}
}
