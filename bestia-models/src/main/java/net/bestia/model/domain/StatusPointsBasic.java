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
public class StatusPointsBasic implements Serializable, StatusPoints {

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

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getCurrentHp()
	 */
	@Override
	public int getCurrentHp() {
		return currentHp;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setCurrentHp(int)
	 */
	@Override
	public void setCurrentHp(int hp) {
		this.currentHp = hp;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getMaxHp()
	 */
	@Override
	public int getMaxHp() {
		return maxHp;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setMaxHp(int)
	 */
	@Override
	public void setMaxHp(int maxHp) {
		this.maxHp = maxHp;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getCurrentMana()
	 */
	@Override
	public int getCurrentMana() {
		return currentMana;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setCurrentMana(int)
	 */
	@Override
	public void setCurrentMana(int mana) {
		this.currentMana = mana;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setMaxMana(int)
	 */
	@Override
	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getMaxMana()
	 */
	@Override
	public int getMaxMana() {
		return maxMana;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getArmorDef()
	 */
	@Override
	public int getArmorDef() {
		return armorDef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setArmorDef(int)
	 */
	@Override
	public void setArmorDef(int armorDef) {
		this.armorDef = armorDef;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getArmorSpDef()
	 */
	@Override
	public int getArmorSpDef() {
		return armorSpDef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setArmorSpDef(int)
	 */
	@Override
	public void setArmorSpDef(int armorSpDef) {
		this.armorSpDef = armorSpDef;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getAtk()
	 */
	@Override
	public int getAtk() {
		return atk;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setAtk(int)
	 */
	@Override
	public void setAtk(int atk) {
		this.atk = atk;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getDef()
	 */
	@Override
	public int getDef() {
		return def;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setDef(int)
	 */
	@Override
	public void setDef(int def) {
		this.def = def;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpAtk()
	 */
	@Override
	public int getSpAtk() {
		return spAtk;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpAtk(int)
	 */
	@Override
	public void setSpAtk(int spAtk) {
		this.spAtk = spAtk;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpDef()
	 */
	@Override
	public int getSpDef() {
		return spDef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpDef(int)
	 */
	@Override
	public void setSpDef(int spDef) {
		this.spDef = spDef;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#getSpd()
	 */
	@Override
	public int getSpd() {
		return spd;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setSpd(int)
	 */
	@Override
	public void setSpd(int spd) {
		this.spd = spd;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#setMaxValues(int, int)
	 */
	@Override
	public void setMaxValues(int maxHp, int maxMana) {

		this.maxHp = maxHp;
		this.maxMana = maxMana;
		checkInvalidStatusValue();
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.StatusPoints#add(net.bestia.model.domain.StatusPoints)
	 */
	@Override
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
		return String.format("SP[atk: %d def: %d, spAtk: %d,"
				+ " spDef: %d, spd: %d, armDef: %d, armSpDef: %d]",
				atk,
				def,
				spAtk,
				spDef,
				spd,
				armorDef,
				armorSpDef);
	}
}
