package net.bestia.model.battle;

import java.io.Serializable;

public class StatusBasedValueModifier implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private float hpRegenMod = 1f;
	private int hpRegenValue = 0;
	
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

}
