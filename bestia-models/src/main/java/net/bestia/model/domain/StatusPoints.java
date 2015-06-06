package net.bestia.model.domain;

import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusPoints {
	@JsonProperty("cHp")
	private int currentHp;
	@JsonProperty("mHp")
	@Transient
	private int maxHp;
	@JsonProperty("cMana")
	private int currentMana;
	@JsonProperty("mMana")
	@Transient
	private int maxMana;
	@JsonProperty("aD")
	@Transient
	private int armorDef;
	@JsonProperty("aSpD")
	@Transient
	private int armorSpDef;
	@JsonProperty
	@Transient
	private int atk;
	@JsonProperty
	@Transient
	private int def;
	@JsonProperty
	@Transient
	private int spAtk;
	@JsonProperty
	@Transient
	private int spDef;
	@JsonProperty
	private int spd;

	public int getCurrentHp() {
		return currentHp;
	}

	public void setCurrentHp(int hp) {
		this.currentHp = hp;
		checkInvalidStatusValue();
	}

	public int getMaxHp() {
		return maxHp;
	}

	public void setMaxHp(int maxHp) {
		this.maxHp = maxHp;
		checkInvalidStatusValue();
	}

	public int getCurrentMana() {
		return currentMana;
	}

	public void setCurrentMana(int mana) {
		this.currentMana = mana;
		checkInvalidStatusValue();
	}

	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;
		checkInvalidStatusValue();
	}

	public int getMaxMana() {
		return maxMana;
	}

	public int getArmorDef() {
		return armorDef;
	}

	public void setArmorDef(int armorDef) {
		this.armorDef = armorDef;
	}

	public int getArmorSpDef() {
		return armorSpDef;
	}

	public void setArmorSpDef(int armorSpDef) {
		this.armorSpDef = armorSpDef;
	}

	public int getAtk() {
		return atk;
	}

	public void setAtk(int atk) {
		this.atk = atk;
	}

	public int getDef() {
		return def;
	}

	public void setDef(int def) {
		this.def = def;
	}

	public int getSpAtk() {
		return spAtk;
	}

	public void setSpAtk(int spAtk) {
		this.spAtk = spAtk;
	}

	public int getSpDef() {
		return spDef;
	}

	public void setSpDef(int spDef) {
		this.spDef = spDef;
	}

	public int getSpd() {
		return spd;
	}

	public void setSpd(int spd) {
		this.spd = spd;
	}

	/**
	 * Adds some other status points to this object.
	 * 
	 * @param rhs
	 */
	public void add(StatusPoints rhs) {
		/*
		 * this.atk += rhs.getAtk(); this.def += rhs.getDef(); this.curHp += rhs.getCurrentHp(); this.curMana +=
		 * rhs.getCurrentMana(); this.spAtk += rhs.getSpAtk(); this.spd += rhs.getSpd(); this.spDef += rhs.getSpDef();
		 * checkInvalidStatusValue();
		 */
	}

	/**
	 * Überprüft ob sich ein Statuswert im "illegalen" Bereich aufhält, zb das die cur_hp immer niedriger sind als die
	 * max_hp. Bei änderungen an kritischen Stati wird diese Methode gecalled um evtl Berichtigungen durchzuführen.
	 * 
	 * @param $changed_stat
	 * @return void
	 */
	private void checkInvalidStatusValue() {

		/*
		 * Überprüft ob gewisse Statuswerte ihren max. Wert überschritten haben, wenn ja werden sie am max. gecapped.
		 * Normal ist dies HP und Mana.
		 */

		if (currentHp > maxHp) {
			currentHp = maxHp;
		}

		if (currentMana > maxMana) {
			currentMana = maxMana;
		}

		if (currentHp < 0) {
			currentHp = 0;
		}

		if (currentMana < 0) {
			currentMana = 0;
		}

		if (armorDef < 1) {
			armorDef = 1;
		}

		if (armorSpDef < 1) {
			armorSpDef = 1;
		}

	}
}
