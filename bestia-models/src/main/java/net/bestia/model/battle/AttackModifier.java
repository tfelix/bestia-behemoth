package net.bestia.model.battle;

/**
 * The {@link AttackModifier} is used to numerically alter attack parameters.
 * 
 * @author Thomas Felix
 *
 */
public class AttackModifier {

	/**
	 * Modifier. The come in two variations:
	 * 
	 * *Mod: This multiplies with the attack value. *Value: This adds or
	 * subtracts from the attack value.
	 */
	private float strengthMod = 1f;
	private int strengthValue = 0;
	private float manaCostMod = 1f;
	private int manaCostValue = 0;
	private float rangeMod = 1f;
	private int rangeValue = 0;
	private float cooldownMod = 1f;
	private int cooldownValue = 0;

	public float getStrengthMod() {
		return strengthMod;
	}

	public void setStrengthMod(float strengthMod) {
		this.strengthMod = strengthMod;
	}

	public int getStrengthValue() {
		return strengthValue;
	}

	public void setStrengthValue(int strengthValue) {
		this.strengthValue = strengthValue;
	}

	public float getManaCostMod() {
		return manaCostMod;
	}

	public void setManaCostMod(float manaCostMod) {
		this.manaCostMod = manaCostMod;
	}

	public int getManaCostValue() {
		return manaCostValue;
	}

	public void setManaCostValue(int manaCostValue) {
		this.manaCostValue = manaCostValue;
	}

	public float getRangeMod() {
		return rangeMod;
	}

	public void setRangeMod(float rangeMod) {
		this.rangeMod = rangeMod;
	}

	public int getRangeValue() {
		return rangeValue;
	}

	public void setRangeValue(int rangeValue) {
		this.rangeValue = rangeValue;
	}

	public float getCooldownMod() {
		return cooldownMod;
	}

	public void setCooldownMod(float cooldownMod) {
		this.cooldownMod = cooldownMod;
	}

	public int getCooldownValue() {
		return cooldownValue;
	}

	public void setCooldownValue(int cooldownValue) {
		this.cooldownValue = cooldownValue;
	}

}
