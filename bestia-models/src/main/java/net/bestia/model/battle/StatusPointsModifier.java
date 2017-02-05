package net.bestia.model.battle;

import java.io.Serializable;

public class StatusPointsModifier implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private float strengthMod = 1f;
	private int strengthValue = 0;
	
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
