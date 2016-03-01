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
	@Transient
	private int currentHp;

	@JsonProperty("mhp")
	private int maxHp;

	@JsonProperty("cmana")
	@Transient
	private int currentMana;

	@JsonProperty("mmana")
	private int maxMana;

	@JsonProperty("adef")
	private int armorDef;

	@JsonProperty("aspdef")
	private int armorSpDef;

	@JsonProperty("atk")
	private int atk;

	@JsonProperty("def")
	private int def;

	@JsonProperty("spatk")
	private int spAtk;

	@JsonProperty("spdef")
	private int spDef;

	@JsonProperty("spd")
	private int spd;
	
	@Transient
	private float hpRegenRate;
	
	@Transient
	private float manaRegenRate;


	public int getCurrentHp() {
		return currentHp;
	}

	public void setCurrentHp(int hp) {
		this.currentHp = hp;
		checkInvalidStatusValue();
	}

	public float getHpRegenerationRate() {
		return hpRegenRate;
	}
	
	public void setHpRegenerationRate(float hpRegenRate) {
		this.hpRegenRate = hpRegenRate;
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
	
	public float getManaRegenerationRate() {
		return manaRegenRate;
	}
	
	public void setManaRegenenerationRate(float manaRegenRate) {
		this.manaRegenRate = manaRegenRate;
	}

	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getMaxMana()
	 */
	
	public int getMaxMana() {
		return maxMana;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getArmorDef()
	 */
	
	public int getArmorDef() {
		return armorDef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setArmorDef(int)
	 */
	
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

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setDef(int)
	 */
	
	public void setDef(int def) {
		this.def = def;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpAtk()
	 */
	
	public int getSpAtk() {
		return spAtk;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpAtk(int)
	 */
	
	public void setSpAtk(int spAtk) {
		this.spAtk = spAtk;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpDef()
	 */
	
	public int getSpDef() {
		return spDef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpDef(int)
	 */
	
	public void setSpDef(int spDef) {
		this.spDef = spDef;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpd()
	 */
	
	public int getSpd() {
		return spd;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpd(int)
	 */
	
	public void setSpd(int spd) {
		this.spd = spd;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setMaxValues(int, int)
	 */
	
	public void setMaxValues(int maxHp, int maxMana) {

		this.maxHp = maxHp;
		this.maxMana = maxMana;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#add(net.bestia.model.domain.StatusPoints)
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
	 * Überprüft ob sich ein Statuswert im "illegalen" Bereich aufhält, zb das
	 * die cur_hp immer niedriger sind als die max_hp. Bei änderungen an
	 * kritischen Stati wird diese Methode gecalled um evtl Berichtigungen
	 * durchzuführen.
	 * 
	 * @param $changed_stat
	 * @return void
	 */
	private void checkInvalidStatusValue() {

		// MAX HP & MANA TEST
		if (maxHp < 1) {
			maxHp = 1;
		}

		if (maxMana < 1) {
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

		if (atk < 0) {
			atk = 0;
		}

		if (def < 0) {
			def = 0;
		}

		if (spd < 0) {
			spd = 0;
		}

		if (spAtk < 0) {
			spAtk = 0;
		}

		if (spDef < 0) {
			spDef = 0;
		}
	}

	@Override
	public String toString() {
		return String.format("SP[curHp: %d, maxHp: %d, curMana: %d, maxMana: %d, atk: %d def: %d, spAtk: %d,"
				+ " spDef: %d, spd: %d, armDef: %d, armSpDef: %d]",
				currentHp,
				maxHp,
				currentMana,
				maxMana,
				atk,
				def,
				spAtk,
				spDef,
				spd,
				armorDef,
				armorSpDef);
	}
}
