package net.bestia.zoneserver.battle;

import bestia.model.domain.Element;

/**
 * A set of variables which are used to modify a running damage calculation
 * operation. These variable set is piped through a script environment and can
 * be modified by attack scripts or by all equipment scripts for each incoming
 * attack.
 * 
 * @author Thomas Felix
 *
 */
public final class DamageVariables {

	// Attack
	private int attackPhysicalBonus = 0;
	private int attackMagicBonus = 0;
	private float attackMagicMod = 1f;
	private float attackPhysicalMod = 1f;
	private float attackRangedMod = 1f;
	private float attackMeleeMod = 1f;

	// Weapon
	private float weaponMod = 1.f;

	// Critical
	private float criticalChanceMod = 1.f;
	private float criticalDamageMod = 1.f;

	// Defenses
	private float physicalDefenseMod = 1.f;
	private float magicDefenseMod = 1.f;

	// Misc
	private float neededManaMod = 1.f;
	private float attackRangeMod = 1.f;
	private int attackRangeBonus = 0;

	private boolean isCriticalHit = false;

	public DamageVariables() {
		// no op.
	}

	/**
	 * Before the variables are used a capped version must be retrieved. It is
	 * not capped during calculation because some values might go into negative
	 * values and can be canceled out this way without getting capped
	 * beforehand.
	 * 
	 * @return
	 */
	public DamageVariables getCappedValues() {
		final DamageVariables vars = new DamageVariables();
		// FIXME Das hier noch machen.
		return vars;
	}

	/**
	 * Adds all values from the argument to the local values and return a new
	 * damage variable object.
	 * 
	 * @param rhs
	 * @return
	 */
	public DamageVariables add(DamageVariables rhs) {
		final DamageVariables vars = new DamageVariables();

		// Attack
		vars.setAttackPhysicalBonus(getAttackPhysicalBonus() + rhs.getAttackPhysicalBonus());
		vars.setAttackMagicBonus(getAttackMagicBonus() + rhs.getAttackMagicBonus());
		vars.setAttackMagicMod(getAttackMagicMod() + rhs.getAttackMagicMod());
		vars.setAttackPhysicalMod(getAttackPhysicalMod() + rhs.getAttackPhysicalMod());
		vars.setAttackRangedMod(getAttackRangedMod() + rhs.getAttackRangedMod());
		vars.setAttackMeleeMod(getAttackRangedMod() + rhs.getAttackRangedMod());

		// Weapon
		vars.setWeaponMod(getWeaponMod() + rhs.getWeaponMod());

		// Critical
		vars.setCriticalChanceMod(getCriticalChanceMod() + rhs.getCriticalChanceMod());
		vars.setCriticalDamageMod(getCriticalDamageMod() + rhs.getCriticalDamageMod());

		// Defenses
		vars.setPhysicalDefenseMod(getCriticalChanceMod() + rhs.getCriticalChanceMod());
		vars.setCriticalDamageMod(getCriticalDamageMod() + rhs.getCriticalDamageMod());
		vars.setPhysicalDefenseMod(getPhysicalDefenseMod() + rhs.getPhysicalDefenseMod());
		vars.setMagicDefenseMod(getMagicDefenseMod() + rhs.getMagicDefenseMod());

		// Misc
		vars.setNeededManaMod(getNeededManaMod() + rhs.getNeededManaMod());
		vars.setAttackRangedMod(getAttackRangedMod() + rhs.getAttackRangedMod());
		vars.setAttackRangeBonus(getAttackRangeBonus() + rhs.getAttackRangeBonus());

		return vars;
	}

	public float getElementBonusMod(Element atkEle) {
		return 0;
	}

	public int getAttackPhysicalBonus() {
		return attackPhysicalBonus;
	}

	public void setAttackPhysicalBonus(int attackPhysicalBonus) {
		this.attackPhysicalBonus = attackPhysicalBonus;
	}

	public int getAttackMagicBonus() {
		return attackMagicBonus;
	}

	public void setAttackMagicBonus(int attackMagicBonus) {
		this.attackMagicBonus = attackMagicBonus;
	}

	public float getWeaponMod() {
		return weaponMod;
	}

	public void setWeaponMod(float weaponMod) {
		this.weaponMod = weaponMod;
	}

	public float getAttackMagicMod() {
		return attackMagicMod;
	}

	public void setAttackMagicMod(float attackMagicMod) {
		this.attackMagicMod = attackMagicMod;
	}

	public float getAttackPhysicalMod() {
		return attackPhysicalMod;
	}

	public void setAttackPhysicalMod(float attackPhysicalMod) {
		this.attackPhysicalMod = attackPhysicalMod;
	}

	public float getAttackRangedMod() {
		return attackRangedMod;
	}

	public void setAttackRangedMod(float attackRangedMod) {
		this.attackRangedMod = attackRangedMod;
	}

	public float getAttackMeleeMod() {
		return attackMeleeMod;
	}

	public void setAttackMeleeMod(float attackMeleeMod) {
		this.attackMeleeMod = attackMeleeMod;
	}

	public float getCriticalChanceMod() {
		return criticalChanceMod;
	}

	public void setCriticalChanceMod(float criticalChanceMod) {
		this.criticalChanceMod = criticalChanceMod;
	}

	public float getCriticalDamageMod() {
		return criticalDamageMod;
	}

	public void setCriticalDamageMod(float criticalDamageMod) {
		this.criticalDamageMod = criticalDamageMod;
	}

	public float getPhysicalDefenseMod() {
		return physicalDefenseMod;
	}

	public void setPhysicalDefenseMod(float physicalDefenseMod) {
		this.physicalDefenseMod = physicalDefenseMod;
	}

	public float getMagicDefenseMod() {
		return magicDefenseMod;
	}

	public void setMagicDefenseMod(float magicDefenseMod) {
		this.magicDefenseMod = magicDefenseMod;
	}

	public float getNeededManaMod() {
		return neededManaMod;
	}

	public void setNeededManaMod(float neededManaMod) {
		this.neededManaMod = neededManaMod;
	}

	public float getAttackRangeMod() {
		return attackRangeMod;
	}

	public void setAttackRangeMod(float attackRangeMod) {
		this.attackRangeMod = attackRangeMod;
	}

	public int getAttackRangeBonus() {
		return attackRangeBonus;
	}

	public void setAttackRangeBonus(int attackRangeBonus) {
		this.attackRangeBonus = attackRangeBonus;
	}

	public boolean isCriticalHit() {
		return isCriticalHit;
	}

	public void setCriticalHit(boolean isCriticalHit) {
		this.isCriticalHit = isCriticalHit;
	}
}
