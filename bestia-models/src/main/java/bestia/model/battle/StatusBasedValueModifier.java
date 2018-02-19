package bestia.model.battle;

import java.io.Serializable;

/**
 * Modifier to change the status based values.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusBasedValueModifier implements Serializable {

	private static final long serialVersionUID = 1L;

	private float hpRegenMod = 1f;
	private int hpRegenValue = 0;
	private float manaRegenMod = 1f;
	private int manaRegenValue = 0;
	private float critMod = 1f;
	private int critValue = 0;
	private float dodgeMod = 1f;
	private int dodgeValue = 0;
	private float castMod = 1f;
	private int castValue = 0;
	private float castDurationMod = 1f;
	private int castDurationValue = 0;
	private float willpowerResMod = 1f;
	private int willpowerResValue = 0;
	private float vitalityResMod = 1f;
	private int vitalityResValue = 0;
	private float hitrateMod = 1f;
	private int hitrateValue = 0;
	private float minDamageMod = 1f;
	private int minDamageValue = 0;
	private float rangedBonusDamageMod = 1f;
	private int rangedBonusDamageValue = 0;
	private float attackSpeedMod = 1f;
	private int attackSpeedValue = 0;
	private float walkspeedMod = 1f;
	private int walkspeedValue = 0;

	public void setHpRegenMod(float hpRegenMod) {
		this.hpRegenMod = hpRegenMod;
	}

	public float getHpRegenMod() {
		return hpRegenMod;
	}

	public void setHpRegenValue(int hpRegenValue) {
		this.hpRegenValue = hpRegenValue;
	}

	public int getHpRegenValue() {
		return hpRegenValue;
	}

	public float getManaRegenMod() {
		return manaRegenMod;
	}

	public void setManaRegenMod(float manaRegenMod) {
		this.manaRegenMod = manaRegenMod;
	}

	public int getManaRegenValue() {
		return manaRegenValue;
	}

	public void setManaRegenValue(int manaRegenValue) {
		this.manaRegenValue = manaRegenValue;
	}

	public float getCritMod() {
		return critMod;
	}

	public void setCritMod(float critMod) {
		this.critMod = critMod;
	}

	public int getCritValue() {
		return critValue;
	}

	public void setCritValue(int critValue) {
		this.critValue = critValue;
	}

	public float getDodgeMod() {
		return dodgeMod;
	}

	public void setDodgeMod(float dodgeMod) {
		this.dodgeMod = dodgeMod;
	}

	public int getDodgeValue() {
		return dodgeValue;
	}

	public void setDodgeValue(int dodgeValue) {
		this.dodgeValue = dodgeValue;
	}

	public float getCastMod() {
		return castMod;
	}

	public void setCastMod(float castMod) {
		this.castMod = castMod;
	}

	public int getCastValue() {
		return castValue;
	}

	public void setCastValue(int castValue) {
		this.castValue = castValue;
	}

	public float getCastDurationMod() {
		return castDurationMod;
	}

	public void setCastDurationMod(float castDurationMod) {
		this.castDurationMod = castDurationMod;
	}

	public int getCastDurationValue() {
		return castDurationValue;
	}

	public void setCastDurationValue(int castDurationValue) {
		this.castDurationValue = castDurationValue;
	}

	public float getWillpowerResMod() {
		return willpowerResMod;
	}

	public void setWillpowerResMod(float willpowerResMod) {
		this.willpowerResMod = willpowerResMod;
	}

	public int getWillpowerResValue() {
		return willpowerResValue;
	}

	public void setWillpowerResValue(int willpowerResValue) {
		this.willpowerResValue = willpowerResValue;
	}

	public float getVitalityResMod() {
		return vitalityResMod;
	}

	public void setVitalityResMod(float vitalityResMod) {
		this.vitalityResMod = vitalityResMod;
	}

	public int getVitalityResValue() {
		return vitalityResValue;
	}

	public void setVitalityResValue(int vitalityResValue) {
		this.vitalityResValue = vitalityResValue;
	}

	public float getHitrateMod() {
		return hitrateMod;
	}

	public void setHitrateMod(float hitrateMod) {
		this.hitrateMod = hitrateMod;
	}

	public int getHitrateValue() {
		return hitrateValue;
	}

	public void setHitrateValue(int hitrateValue) {
		this.hitrateValue = hitrateValue;
	}

	public float getMinDamageMod() {
		return minDamageMod;
	}

	public void setMinDamageMod(float minDamageMod) {
		this.minDamageMod = minDamageMod;
	}

	public int getMinDamageValue() {
		return minDamageValue;
	}

	public void setMinDamageValue(int minDamageValue) {
		this.minDamageValue = minDamageValue;
	}

	public float getRangedBonusDamageMod() {
		return rangedBonusDamageMod;
	}

	public void setRangedBonusDamageMod(float rangedBonusDamageMod) {
		this.rangedBonusDamageMod = rangedBonusDamageMod;
	}

	public int getRangedBonusDamageValue() {
		return rangedBonusDamageValue;
	}

	public void setRangedBonusDamageValue(int rangedBonusDamageValue) {
		this.rangedBonusDamageValue = rangedBonusDamageValue;
	}

	public float getAttackSpeedMod() {
		return attackSpeedMod;
	}

	public void setAttackSpeedMod(float attackSpeedMod) {
		this.attackSpeedMod = attackSpeedMod;
	}

	public int getAttackSpeedValue() {
		return attackSpeedValue;
	}

	public void setAttackSpeedValue(int attackSpeedValue) {
		this.attackSpeedValue = attackSpeedValue;
	}

	public float getWalkspeedMod() {
		return walkspeedMod;
	}

	public void setWalkspeedMod(float walkspeedMod) {
		this.walkspeedMod = walkspeedMod;
	}

	public int getWalkspeedValue() {
		return walkspeedValue;
	}

	public void setWalkspeedValue(int walkspeedValue) {
		this.walkspeedValue = walkspeedValue;
	}
}
