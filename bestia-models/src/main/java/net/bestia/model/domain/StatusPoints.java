package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class StatusPoints implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("chp")
	private int currentHp;

	@JsonProperty("mhp")
	@Transient
	private int maxHp;

	@JsonProperty("cmana")
	private int currentMana;

	@JsonProperty("mmana")
	@Transient
	private int maxMana;

	@JsonProperty("adef")
	@Transient
	private int armorDef;

	@JsonProperty("aspdef")
	@Transient
	private int armorSpDef;

	@JsonProperty("atk")
	@Transient
	private int atk;

	@JsonProperty("def")
	@Transient
	private int def;

	@JsonProperty("spatk")
	@Transient
	private int spAtk;

	@JsonProperty("spdef")
	@Transient
	private int spDef;

	@JsonProperty("spd")
	@Transient
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
		checkInvalidStatusValue();
	}

	public int getArmorSpDef() {
		return armorSpDef;
	}

	public void setArmorSpDef(int armorSpDef) {
		this.armorSpDef = armorSpDef;
		checkInvalidStatusValue();
	}

	public int getAtk() {
		return atk;
	}

	public void setAtk(int atk) {
		this.atk = atk;
		checkInvalidStatusValue();
	}

	public int getDef() {
		return def;
	}

	public void setDef(int def) {
		this.def = def;
		checkInvalidStatusValue();
	}

	public int getSpAtk() {
		return spAtk;
	}

	public void setSpAtk(int spAtk) {
		this.spAtk = spAtk;
		checkInvalidStatusValue();
	}

	public int getSpDef() {
		return spDef;
	}

	public void setSpDef(int spDef) {
		this.spDef = spDef;
		checkInvalidStatusValue();
	}

	public int getSpd() {
		return spd;
	}

	public void setSpd(int spd) {
		this.spd = spd;
		checkInvalidStatusValue();
	}

	/**
	 * Special setter to avoid the clearance of any of the current mana or current hp value wich will occure if one set
	 * a single value but the other value has not yet been set. Either one of the values will get reset. To avoid this
	 * use this method and set the limiting values at the same time.
	 * 
	 * @param maxHp
	 * @param maxMana
	 */
	public void setMaxValues(int maxHp, int maxMana) {

		this.maxHp = maxHp;
		this.maxMana = maxMana;
		checkInvalidStatusValue();
		
	}

	/**
	 * Adds some other status points to this object.
	 * 
	 * @param rhs
	 */
	public void add(StatusPoints rhs) {
		
		this.maxHp += rhs.getMaxHp();
		this.maxMana += rhs.getMaxMana();
		this.currentHp += rhs.getCurrentHp();
		this.currentMana += rhs.getCurrentMana();

		this.atk += rhs.getAtk();
		this.def += rhs.getDef();
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

		// MAX HP & MANA TEST
		if(maxHp < 1) {
			maxHp = 1;
		}
		
		if(maxMana < 1) {
			maxMana = 1;
		}
		
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

		// ARMOR TEST
		if (armorDef < 1) {
			armorDef = 1;
		}
		if (armorDef > 100) {
			armorDef = 100;
		}

		// SP ARMOR TEST
		if (armorSpDef < 1) {
			armorSpDef = 1;
		}
		if (armorSpDef > 100) {
			armorSpDef = 100;
		}
		
		if(atk < 0) {
			atk = 0;
		}
		
		if(def < 0) {
			def = 0;
		}
		
		if(spd < 0) {
			spd = 0;
		}
		
		if(spAtk < 0) {
			spAtk = 0;
		}
		
		if(spDef < 0) {
			spDef = 0;
		}
	}
}
