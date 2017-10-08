package net.bestia.zoneserver.battle;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.battle.Damage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackType;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;

@Component
public class DamageCalculator {

	private static final Logger LOG = LoggerFactory.getLogger(DamageCalculator.class);

	private final Random rand = ThreadLocalRandom.current();

	private final EntityService entityService;

	@Autowired
	public DamageCalculator(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

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

		final float baseAtk = getBaseAtk(ctx, dmgVars);
		final float atkMod = getAttackMod(ctx.getUsedAttack(), dmgVars);
		final float hardDefMod = getHardDefMod(ctx, dmgVars);
		final float critMod = getCritMod(ctx, dmgVars);
		final int softDef = getSoftDef(ctx, dmgVars);

		final int damage = (int) Math.floor((baseAtk * atkMod * hardDefMod * critMod) - softDef);

		if (critMod > 1) {
			return Damage.getCrit(damage);
		} else {
			return Damage.getHit(damage);
		}
	}

	private int getSoftDef(BattleContext ctx, DamageVariables dmgVars) {

		final AttackType type = ctx.getUsedAttack().getType();
		final Entity defender = ctx.getDefender();
		final int defenderLv = entityService.getComponent(defender, LevelComponent.class).get().getLevel();

		final StatusPoints defPoints = ctx.getDefenderStatusPoints();

		float softDef = 0;

		if (type == AttackType.MELEE_MAGIC || type == AttackType.RANGED_MAGIC) {
			// Magic attack.
			softDef = defenderLv / 2f + defPoints.getVitality() + defPoints.getStrength() / 3f;
		} else {
			// physical attack.
			softDef = defenderLv / 2f + defPoints.getVitality() + defPoints.getWillpower() / 4f
					+ defPoints.getIntelligence() / 5f;
		}

		return (int) Math.max(0, softDef);
	}

	private float getCritMod(BattleContext ctx, DamageVariables dmgVars) {

		final float baseCritRate = toMod(ctx.getAttackerStatusBased().getCriticalHitrate());

		if (rand.nextFloat() > baseCritRate) {
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

	private float getBaseAtk(BattleContext ctx, DamageVariables dmgVars) {
		final float statusAtk = getStatusAtk(ctx);
		final float varMod = getVarMod();
		final int weaponAtk = getWeaponAtk();
		final int ammoAtk = getAmmoAtk(ctx);
		final float varModReduced = varMod - varMod / 2 - 0.5f;
		final float elementMod = getElementMod(ctx);
		final int bonusAtk = dmgVars.getBonusAttack();

		return (2 * statusAtk * varMod + weaponAtk * varModReduced + ammoAtk + bonusAtk) * elementMod;
	}

	private float getElementMod(BattleContext ctx) {
		return ElementModifier.getModifier(Element.NORMAL, Element.NORMAL);
	}

	private int getAmmoAtk(BattleContext ctx) {
		if (ctx.getUsedAttack().getType() == AttackType.RANGED_PHYSICAL) {
			LOG.warn("Currently equipment is not possible. Ranged weapon atk is 0.");
			return 0;
		} else {
			return 0;
		}
	}

	private int getWeaponAtk() {
		return 0;
	}

	private float getVarMod() {
		// TODO Auto-generated method stub
		return 0;
	}

	private float getStatusAtk(BattleContext ctx) {

		final Entity attacker = ctx.getAttacker();
		
		final Attack atk = ctx.getUsedAttack();

		final int level = entityService.getComponent(attacker, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(1);
		
		final StatusPoints statusPoints = entityService.getComponent(attacker, StatusComponent.class)
				.get()
				.getStatusPoints();

		if(atk.getType() == AttackType.MELEE_PHYSICAL) {
			return level / 4.0f + statusPoints.getStrength() + statusPoints.getDexterity() / 5.f;
		} else if(atk.getType() == AttackType.RANGED_PHYSICAL) {
			return level / 4.0f + statusPoints.getDexterity() + statusPoints.getStrength() / 5.f;
		} else {
			// Magical attack.
			return level / 4.0f + statusPoints.getIntelligence() + statusPoints.getWillpower() / 5.f;
		}
	}

	private float between(float x, float min, float max) {
		return Math.max(Math.max(x, min), min);
	}

	private float toMod(int x) {
		return x / 100f;
	}
}
