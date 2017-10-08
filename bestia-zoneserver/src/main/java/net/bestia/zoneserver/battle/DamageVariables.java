package net.bestia.zoneserver.battle;

/**
 * A set of variables which are used to modify a running damage calculation
 * operation. These variable set is piped through a script environment and can
 * be modified by attack scripts or by all equipment scripts.
 * 
 * @author Thomas Felix
 *
 */
public final class DamageVariables {

	private int bonusPhysicalMeleeDamage = 0;
	private int bonusMagicMeleeDamage = 0;
	private int bonusPhysicalRangedDamage = 0;
	private int bonusMagicRangedDamage = 0;
	
	private float armor = 1.f;
	private float magicArmor = 1.f;
	
	private float bonusCritMod = 1.f;
	
	//private int bonusAttack = 0;
	
	public DamageVariables() {
		// no op.
	}

	/**
	 * Before the variables are used a capped version must be retrieved. It is
	 * not capped during calculation because some values might go into negative
	 * values and can be canceled out this way without getting capped beforehand.
	 * 
	 * @return
	 */
	public DamageVariables getCappedValues() {

		return null;
	}

	public int getBonusPhysicalMeleeDamage() {
		return bonusPhysicalMeleeDamage;
	}

	public void setBonusPhysicalMeleeDamage(int bonusPhysicalMeleeDamage) {
		this.bonusPhysicalMeleeDamage = bonusPhysicalMeleeDamage;
	}

	public int getBonusMagicMeleeDamage() {
		return bonusMagicMeleeDamage;
	}

	public void setBonusMagicMeleeDamage(int bonusMagicMeleeDamage) {
		this.bonusMagicMeleeDamage = bonusMagicMeleeDamage;
	}

	public int getBonusPhysicalRangedDamage() {
		return bonusPhysicalRangedDamage;
	}

	public void setBonusPhysicalRangedDamage(int bonusPhysicalRangedDamage) {
		this.bonusPhysicalRangedDamage = bonusPhysicalRangedDamage;
	}

	public float getArmorMod() {
		return armor;
	}

	public void setArmorMod(float armorMod) {
		this.armor = armorMod;
	}

	public float getMagicResist() {
		return magicArmor;
	}

	public void setMagicResist(float magicResist) {
		this.magicArmor = magicResist;
	}

	public float getBonusCritMod() {
		return bonusCritMod;
	}

	public void setBonusCritMod(float bonusCritMod) {
		this.bonusCritMod = bonusCritMod;
	}

	/*
	public int getBonusAttack() {
		return bonusAttack;
	}

	public void setBonusAttack(int bonusAttack) {
		this.bonusAttack = bonusAttack;
	}
*/
	public int getBonusMagicRangedDamage() {
		return bonusMagicRangedDamage;
	}

	public void setBonusMagicRangedDamage(int bonusMagicRangedDamage) {
		this.bonusMagicRangedDamage = bonusMagicRangedDamage;
	}

	public int getBonusAttack() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
}
