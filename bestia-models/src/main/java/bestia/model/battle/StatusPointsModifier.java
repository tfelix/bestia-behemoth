package bestia.model.battle;

import java.io.Serializable;

public class StatusPointsModifier implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private float strengthMod = 1f;
	private int strengthValue = 0;
	
	private float vitalityMod = 1f;
	private int vitalityValue = 0;
	
	private float intelligenceMod = 1f;
	private int intelligenceValue = 0;
	
	private float agilityMod = 1f;
	private int agilityValue = 0;
	
	private float willpowerMod = 1f;
	private int willpowerValue = 0;
	
	private float dexterityMod = 1f;
	private int dexterityValue = 0;
	
	private float defenseMod = 1f;
	private int defenseValue = 0;
	
	private float magicDefenseMod = 1f;
	private int magicDefenseValue = 0;
	
	private float maxHpMod = 1f;
	private int maxHpValue = 0;
	
	private float maxManaMod = 1f;
	private int maxManaValue = 0;
	
	
	public float getMaxHpMod() {
		return maxHpMod;
	}

	public void setMaxHpMod(float maxHpMod) {
		this.maxHpMod = maxHpMod;
	}

	public int getMaxHpValue() {
		return maxHpValue;
	}

	public void setMaxHpValue(int maxHpValue) {
		this.maxHpValue = maxHpValue;
	}

	public float getMaxManaMod() {
		return maxManaMod;
	}

	public void setMaxManaMod(float maxManaMod) {
		this.maxManaMod = maxManaMod;
	}

	public int getMaxManaValue() {
		return maxManaValue;
	}

	public void setMaxManaValue(int maxManaValue) {
		this.maxManaValue = maxManaValue;
	}

	public float getVitalityMod() {
		return vitalityMod;
	}

	public void setVitalityMod(float vitalityMod) {
		this.vitalityMod = vitalityMod;
	}

	public int getVitalityValue() {
		return vitalityValue;
	}

	public void setVitalityValue(int vitalityValue) {
		this.vitalityValue = vitalityValue;
	}

	public float getIntelligenceMod() {
		return intelligenceMod;
	}

	public void setIntelligenceMod(float intelligenceMod) {
		this.intelligenceMod = intelligenceMod;
	}

	public int getIntelligenceValue() {
		return intelligenceValue;
	}

	public void setIntelligenceValue(int intelligenceValue) {
		this.intelligenceValue = intelligenceValue;
	}

	public float getAgilityMod() {
		return agilityMod;
	}

	public void setAgilityMod(float agilityMod) {
		this.agilityMod = agilityMod;
	}

	public int getAgilityValue() {
		return agilityValue;
	}

	public void setAgilityValue(int agilityValue) {
		this.agilityValue = agilityValue;
	}

	public float getWillpowerMod() {
		return willpowerMod;
	}

	public void setWillpowerMod(float willpowerMod) {
		this.willpowerMod = willpowerMod;
	}

	public int getWillpowerValue() {
		return willpowerValue;
	}

	public void setWillpowerValue(int willpowerValue) {
		this.willpowerValue = willpowerValue;
	}

	public float getDexterityMod() {
		return dexterityMod;
	}

	public void setDexterityMod(float dexterityMod) {
		this.dexterityMod = dexterityMod;
	}

	public int getDexterityValue() {
		return dexterityValue;
	}

	public void setDexterityValue(int dexterityValue) {
		this.dexterityValue = dexterityValue;
	}

	public float getDefenseMod() {
		return defenseMod;
	}

	public void setDefenseMod(float defenseMod) {
		this.defenseMod = defenseMod;
	}

	public int getDefenseValue() {
		return defenseValue;
	}

	public void setDefenseValue(int defenseValue) {
		this.defenseValue = defenseValue;
	}

	public float getMagicDefenseMod() {
		return magicDefenseMod;
	}

	public void setMagicDefenseMod(float magicDefenseMod) {
		this.magicDefenseMod = magicDefenseMod;
	}

	public int getMagicDefenseValue() {
		return magicDefenseValue;
	}

	public void setMagicDefenseValue(int magicDefenseValue) {
		this.magicDefenseValue = magicDefenseValue;
	}

	public void setStrengthMod(float mod) {
		this.strengthMod = mod;
	}
	
	public void setStrengthValue(int value) {
		this.strengthValue = value;
	}
	
	public float getStrengthMod() {
		return strengthMod;
	}
	
	public int getStrengthValue() {
		return strengthValue;
	}
}
