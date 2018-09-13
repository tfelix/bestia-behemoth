package net.bestia.zoneserver.battle;

import net.bestia.model.battle.Damage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This calculates the raw damage of an attack if all other variables are known.
 * Even though no member state is saved this calculator is instanced as an
 * object in order to perform some kind of strategy pattern so the damage
 * calculation can be switched during runtime.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class DamageCalculator {

	private final static Logger LOG = LoggerFactory.getLogger(DamageCalculator.class);

	private final Random rand = ThreadLocalRandom.current();

	/**
	 * This calculates the taken battle damage. Currently this is only a
	 * placeholder until the real damage formula is invented.
	 * 
	 * Please not that this method ONLY calculates the damage. If the attack is
	 * controlled by a script this wont get checked by this method anymore. Only
	 * raw damage calculation is performed.
	 */
	public Damage calculateDamage(BattleContext battleCtx) {

		final float baseAtk = getBaseAttack(battleCtx);
		final float atkMod = getAttackModifier(battleCtx);
		final float hardDefMod = getHardDefenseModifier(battleCtx);
		final float critMod = getCritModifier(battleCtx);
		final float softDef = getSoftDefense(battleCtx);

		final int damage = (int) Math.floor(baseAtk * atkMod * hardDefMod * critMod - softDef);

		LOG.trace("Damage (melee): {}.", damage);

		return Damage.getHit(damage);
	}

	private float getBaseAttack(BattleContext battleCtx) {
		
		final Attack usedAttack = battleCtx.getUsedAttack();
		final DamageVariables dmgVars = battleCtx.getDamageVariables();

		final float statusAtk = getStatusAttack(battleCtx);
		final float varMod = battleCtx.getDamageVariables().isCriticalHit() ? 1f : calculateVarMod();
		final float weaponAtk = calculateWeaponAtk();
		final float varModRed = varMod - varMod / 2 - 0.5f;
		final float ammaAtk = usedAttack.isRanged() ? getAmmoAtk(battleCtx) : 0f;
		final int bonusAtk = usedAttack.isMagic() ? dmgVars.getAttackMagicBonus() : dmgVars.getAttackPhysicalBonus();
		final float elementMod = getElementMod(battleCtx);
		final float elementBonusMod = getElementBonusMod(battleCtx);

		float baseAtk = 2 * statusAtk * varMod + weaponAtk * varModRed + ammaAtk + bonusAtk;
		baseAtk = baseAtk * elementMod * elementBonusMod;
		baseAtk = Math.min(1, baseAtk);

		LOG.trace("BaseAtk: {}.", baseAtk);

		return baseAtk;
	}

	/**
	 * @return The attack value based solely on the status of the entity.
	 */
	private float getStatusAttack(BattleContext battleCtx) {

		final Attack atk = battleCtx.getUsedAttack();
		final float lvMod = battleCtx.getAttackerLevel() / 4;
		final StatusPoints sp = battleCtx.getAttackerStatusPoints();

		final float statusAtk;
		if (atk.isMagic()) {
			statusAtk = lvMod + sp.getStrength() + sp.getDexterity() / 5;
			LOG.trace("StatusAtk (magic): {}", statusAtk);
		} else if (atk.isRanged()) {
			statusAtk = lvMod + sp.getDexterity() + sp.getStrength() / 5;
			LOG.trace("StatusAtk (ranged physical): {}", statusAtk);
		} else {
			statusAtk = lvMod + sp.getStrength() + sp.getDexterity() / 5;
			LOG.trace("StatusAtk (melee physical): {}", statusAtk);

		}

		return statusAtk;
	}

	private float getSoftDefense(BattleContext battleCtx) {
		
		final Attack atk = battleCtx.getUsedAttack(); 
		//Entity defender
		final StatusPoints defStatus = battleCtx.getDefenderStatusPoints();
		final int lv = battleCtx.getDefenderLevel();

		float softDef;

		switch (atk.getType()) {
		case MELEE_MAGIC:
		case RANGED_MAGIC:
			softDef = lv / 2.f + defStatus.getVitality() + defStatus.getWillpower() / 4.f
					+ defStatus.getIntelligence() / 5.f;
		case MELEE_PHYSICAL:
		case RANGED_PHYSICAL:
			softDef = lv / 2.f + defStatus.getVitality() + defStatus.getStrength() / 3.f;
		default:
			softDef = 0;
		}

		softDef = Math.min(0f, softDef);

		LOG.trace("SoftDefense: {}.", softDef);

		return softDef;
	}

	private float getHardDefenseModifier(BattleContext battleCtx) {

		final Attack atk = battleCtx.getUsedAttack();
		final DamageVariables dmgVars = battleCtx.getDamageVariables();
		final StatusPoints defStatus = battleCtx.getDefenderStatusPoints();

		float defMod;

		switch (atk.getType()) {
		case MELEE_MAGIC:
		case RANGED_MAGIC:
			defMod = 1 - (defStatus.getMagicDefense() / 100f + dmgVars.getMagicDefenseMod());
		case MELEE_PHYSICAL:
		case RANGED_PHYSICAL:
			defMod = 1 - (defStatus.getDefense() / 100f + dmgVars.getPhysicalDefenseMod());
		default:
			defMod = 1;
		}

		defMod = BattleUtil.between(0.05f, 1.0f, defMod);

		LOG.trace("HardDefenseMod: {}.", defMod);

		return defMod;
	}

	/**
	 * @return Gets the attack modifier of the attack.
	 */
	private float getAttackModifier(BattleContext battleCtx) {

		final Attack atk = battleCtx.getUsedAttack();
		final DamageVariables dmgVars = battleCtx.getDamageVariables();

		float atkMod;

		switch (atk.getType()) {
		case MELEE_MAGIC:
		case MELEE_PHYSICAL:
			atkMod = dmgVars.getAttackMagicMod() * dmgVars.getAttackMeleeMod();
			break;
		case RANGED_MAGIC:
		case RANGED_PHYSICAL:
			atkMod = dmgVars.getAttackRangedMod() * dmgVars.getAttackRangedMod();
			break;
		default:
			atkMod = 1.0f;
		}

		LOG.trace("AttackMod: {}.", atkMod);

		return Math.min(0, atkMod);
	}

	/**
	 * @return The critical modifier.
	 */
	private float getCritModifier(BattleContext battleCtx) {
		
		final DamageVariables dmgVars = battleCtx.getDamageVariables();

		float critMod = dmgVars.isCriticalHit() ? 1.4f * dmgVars.getCriticalDamageMod() : 1.0f;
		critMod = Math.min(1.0f, critMod);

		LOG.trace("CritMod: {}.", critMod);

		return critMod;
	}

	private float getElementMod(BattleContext battleCtx) {
		
		final Element atkEle = battleCtx.getAttackElement();
		final Element defEle = battleCtx.getDefenderElement();
		
		final float eleMod = ElementModifier.getModifier(atkEle, defEle) / 100f;
		LOG.trace("ElementMod: {}", eleMod);
		return eleMod;
	}

	private float getElementBonusMod(BattleContext battleCtx) {
		final Element atkEle = battleCtx.getAttackElement();
		final float eleMod = battleCtx.getDamageVariables().getElementBonusMod(atkEle);
		LOG.trace("ElementBonusMod: {}", eleMod);
		return eleMod;
	}

	private float calculateWeaponAtk() {
		LOG.warn("calculateWeaponAtk is currently not implemented.");

		final float weaponAtk = 10;
		LOG.trace("WeaponAtk: {}", weaponAtk);
		return weaponAtk;
	}
	
	private float getAmmoAtk(BattleContext battleCtx) {
		// TODO Auto-generated method stub
		return 0;
	}

	// HELPER METHODS

	/**
	 * Calculates the variance modification.
	 * 
	 * @return A random value between 0.85 and 1.
	 */
	private float calculateVarMod() {
		return 1 - (rand.nextFloat() * 0.15f);
	}
}
