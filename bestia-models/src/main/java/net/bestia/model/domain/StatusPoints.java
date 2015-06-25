package net.bestia.model.domain;

import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusPoints {
	@JsonProperty("chp")
	private int curHp;

	@JsonProperty("mhp")
	@Transient
	private int maxHp;

	@JsonProperty("cmana")
	private int curMana;

	@JsonProperty("mmana")
	@Transient
	private int maxMana;

	@JsonProperty("adef")
	@Transient
	private int armorDef;

	@JsonProperty("aspdef")
	@Transient
	private int armorSpDef;

	@Transient
	private int atk;

	@Transient
	private int def;

	@Transient
	private int spAtk;

	@Transient
	private int spDef;

	@Transient
	private int spd;

	public int getCurrentHp() {
		return curHp;
	}

	public void setCurrentHp(int hp) {
		this.curHp = hp;
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
		return curMana;
	}

	public void setCurrentMana(int mana) {
		this.curMana = mana;
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

		this.atk += rhs.getAtk();
		this.def += rhs.getDef();
		this.curHp += rhs.getCurrentHp();
		this.curMana += rhs.getCurrentMana();
		this.spAtk += rhs.getSpAtk();
		this.spd += rhs.getSpd();
		this.spDef += rhs.getSpDef();
		this.armorDef += rhs.getArmorDef();
		this.armorSpDef += rhs.getArmorSpDef();
		checkInvalidStatusValue();

	}

	/**
	 * Überprüft ob sich ein Statuswert im "illegalen" Bereich aufhält, zb das die cur_hp immer niedriger sind als die
	 * max_hp. Bei änderungen an kritischen Stati wird diese Methode gecalled um evtl Berichtigungen durchzuführen.
	 * 
	 * @param $changed_stat
	 * @return void
	 */
	private void checkInvalidStatusValue() {

		if (curHp > maxHp) {
			curHp = maxHp;
		}

		if (curMana > maxMana) {
			curMana = maxMana;
		}

		if (curHp < 0) {
			curHp = 0;
		}

		if (curMana < 0) {
			curMana = 0;
		}

		if (armorDef < 1) {
			armorDef = 1;
		}
		
		if(armorDef > 100) {
			armorDef = 100;
		}

		if (armorSpDef < 1) {
			armorSpDef = 1;
		}
		
		if(armorSpDef > 100) {
			armorSpDef = 100;
		}

	}
}
