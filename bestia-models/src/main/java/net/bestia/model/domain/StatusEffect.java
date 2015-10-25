package net.bestia.model.domain;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class StatusEffect {

	@Id
	private int id;
	private String datebaseName;

	private int atkSumMod = 0;
	private float atkMultMod = 1.0f;

	private int defSumMod = 0;
	private float defMultMod = 1.0f;

	private int spAtkSumMod = 0;
	private float spAtkMultMod = 1.0f;

	private int spDefSumMod = 0;
	private float spDefMultMod = 1.0f;

	private int spdSumMod = 0;
	private float spdMultMod = 1.0f;

	private int armorSumMod = 0;
	private float armorMultMod = 1.0f;

	private int spArmorSumMod = 0;
	private float spArmorMultMod = 1.0f;

	public int getId() {
		return id;
	}
	
	public String getDatebaseName() {
		return datebaseName;
	}

	public void setDatebaseName(String datebaseName) {
		this.datebaseName = datebaseName;
	}

	public int getAtkSumMod() {
		return atkSumMod;
	}

	public void setAtkSumMod(int atkSumMod) {
		this.atkSumMod = atkSumMod;
	}

	public float getAtkMultMod() {
		return atkMultMod;
	}

	public void setAtkMultMod(float atkMultMod) {
		this.atkMultMod = atkMultMod;
	}

	public int getDefSumMod() {
		return defSumMod;
	}

	public void setDefSumMod(int defSumMod) {
		this.defSumMod = defSumMod;
	}

	public float getDefMultMod() {
		return defMultMod;
	}

	public void setDefMultMod(float defMultMod) {
		this.defMultMod = defMultMod;
	}

	public int getSpAtkSumMod() {
		return spAtkSumMod;
	}

	public void setSpAtkSumMod(int spAtkSumMod) {
		this.spAtkSumMod = spAtkSumMod;
	}

	public float getSpAtkMultMod() {
		return spAtkMultMod;
	}

	public void setSpAtkMultMod(float spAtkMultMod) {
		this.spAtkMultMod = spAtkMultMod;
	}

	public int getSpDefSumMod() {
		return spDefSumMod;
	}

	public void setSpDefSumMod(int spDefSumMod) {
		this.spDefSumMod = spDefSumMod;
	}

	public float getSpDefMultMod() {
		return spDefMultMod;
	}

	public void setSpDefMultMod(float spDefMultMod) {
		this.spDefMultMod = spDefMultMod;
	}

	public int getSpdSumMod() {
		return spdSumMod;
	}

	public void setSpdSumMod(int spdSumMod) {
		this.spdSumMod = spdSumMod;
	}

	public float getSpdMultMod() {
		return spdMultMod;
	}

	public void setSpdMultMod(float spdMultMod) {
		this.spdMultMod = spdMultMod;
	}

	public int getArmorSumMod() {
		return armorSumMod;
	}

	public void setArmorSumMod(int armorSumMod) {
		this.armorSumMod = armorSumMod;
	}

	public float getArmorMultMod() {
		return armorMultMod;
	}

	public void setArmorMultMod(float armorMultMod) {
		this.armorMultMod = armorMultMod;
	}

	public int getSpArmorSumMod() {
		return spArmorSumMod;
	}

	public void setSpArmorSumMod(int spArmorSumMod) {
		this.spArmorSumMod = spArmorSumMod;
	}

	public float getSpArmorMultMod() {
		return spArmorMultMod;
	}

	public void setSpArmorMultMod(float spArmorMultMod) {
		this.spArmorMultMod = spArmorMultMod;
	}

}
