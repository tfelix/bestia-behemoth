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
	
	private float criticalDamageMod = 1.f;
	
	private float armor = 1.f;
	private float magicArmor = 1.f;
	
	private float neededManaMod = 1.f;	
	private float attackRangeMod = 1.f;
	private int attackRangeBonus = 0;
	
	//private int bonusAttack = 0;
	
	private float criticalMod = 1.f;
	private boolean isCriticalHit = false;
	
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

	public float getArmorMod() {
		return armor;
	}

	public void setArmorMod(float armorMod) {
		this.armor = armorMod;
	}

	public float getMagicResistMod() {
		return magicArmor;
	}

	public void setMagicResistMod(float magicResist) {
		this.magicArmor = magicResist;
	}

	public int getBonusAttack() {
		return 0;
	}
	
	public float getNeededManaMod() {
		return neededManaMod;
	}
	
	public void setNeededManaMod(float neededManaMod) {
		this.neededManaMod = neededManaMod;
	}
	
	public int getAttackRangeBonus() {
		return attackRangeBonus;
	}
	
	public float getAttackRangeMod() {
		return attackRangeMod;
	}
	
	public boolean isCriticalHit() {
		return isCriticalHit;
	}
	
	public void setCriticalHit(boolean isCriticalHit) {
		this.isCriticalHit = isCriticalHit;
	}
	
	public float getCriticalMod() {
		return criticalMod;
	}
	
	public void setCriticalMod(float criticalMod) {
		if(criticalMod < 0) {
			criticalMod = 0;
		}
		this.criticalMod = criticalMod;
	}
	
	public float getCriticalDamageMod() {
		return criticalDamageMod;
	}

	public float getAttackBonus() {
		// TODO Auto-generated method stub
		return 0;
	}

	public float getMagicMeleeAttackMod() {
		return 1;
	}

	public float getPhysicalMeleeAttackMod() {
		return 1;
	}

	public float getMagicRangedAttackMod() {
		return 1;
	}

	public float getPhysicalRangedAttackMod() {
		return 1;
	}
}
