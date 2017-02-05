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
public class StatusPointsImpl implements Serializable, StatusPoints {

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

	@JsonProperty("str")
	private int strength;

	@JsonProperty("vit")
	private int vitality;

	@JsonProperty("int")
	private int intelligence;

	@JsonProperty("will")
	private int willpower;

	@JsonProperty("agi")
	private int agility;

	@JsonProperty("dex")
	private int dexterity;

	@JsonProperty("def")
	private int defense;

	@JsonProperty("mdef")
	private int magicDefense;

	public StatusPointsImpl() {
		// no op.
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getCurrentHp()
	 */
	@Override
	public int getCurrentHp() {
		return currentHp;
	}

	@Override
	public void setCurrentHp(int hp) {
		if (hp < 1) {
			hp = 1;
		}
		if (hp > maxHp) {
			hp = maxHp;
		}

		this.currentHp = hp;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getMaxHp()
	 */
	@Override
	public int getMaxHp() {
		return maxHp;
	}

	@Override
	public void setMaxHp(int maxHp) {
		if (maxHp < 1) {
			maxHp = 1;
		}

		this.maxHp = maxHp;

		if (currentHp > maxHp) {
			setCurrentHp(maxHp);
		}
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getCurrentMana()
	 */
	@Override
	public int getCurrentMana() {
		return currentMana;
	}

	@Override
	public void setCurrentMana(int mana) {
		if (mana < 1) {
			mana = 1;
		}
		if (mana > maxMana) {
			mana = maxMana;
		}

		this.currentMana = mana;
	}

	@Override
	public void setMaxMana(int maxMana) {
		if (maxMana < 1) {
			maxMana = 1;
		}

		this.maxMana = maxMana;

		if (currentMana > maxMana) {
			setCurrentMana(maxMana);
		}
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getMaxMana()
	 */
	@Override
	public int getMaxMana() {
		return maxMana;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getDefense()
	 */
	@Override
	public int getDefense() {
		return defense;
	}

	@Override
	public void setDefense(int def) {
		if (def < 0) {
			def = 0;
		}
		if (def > 1000) {
			def = 1000;
		}

		this.defense = def;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getMagicDefense()
	 */
	@Override
	public int getMagicDefense() {
		return magicDefense;
	}

	/**
	 * Sets the magic defense. Must be between 0 and 1000 (which increments in
	 * 1/10) percents.
	 * 
	 * @param mdef
	 *            The new mdef.
	 */
	@Override
	public void setMagicDefense(int mdef) {
		if (mdef < 0) {
			mdef = 0;
		}
		if (mdef > 1000) {
			mdef = 1000;
		}

		this.magicDefense = mdef;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getStrength()
	 */
	@Override
	public int getStrength() {
		return strength;
	}

	/**
	 * Sets the strength. Can not be lower then 1.
	 * 
	 * @param str
	 *            The new strength.
	 */
	@Override
	public void setStrenght(int str) {
		if (str < 1) {
			str = 1;
		}

		this.strength = str;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getVitality()
	 */
	@Override
	public int getVitality() {
		return vitality;
	}

	/**
	 * Sets the vitality. Can not be lower then 1.
	 * 
	 * @param vit
	 *            The new vit.
	 */
	@Override
	public void setVitality(int vit) {
		if (vit < 1) {
			vit = 1;
		}

		this.vitality = vit;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getIntelligence()
	 */
	@Override
	public int getIntelligence() {
		return intelligence;
	}

	/**
	 * Sets the intelligence. Can not be lower then 1.
	 * 
	 * @param intel
	 *            New intelligence.
	 */
	@Override
	public void setIntelligence(int intel) {
		if (intel < 1) {
			intel = 1;
		}

		this.intelligence = intel;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getAgility()
	 */
	@Override
	public int getAgility() {
		return agility;
	}

	/**
	 * Sets the agility.
	 * 
	 * @param agi
	 *            The new agi value.
	 */
	@Override
	public void setAgility(int agi) {
		if (agi < 1) {
			agi = 1;
		}
		this.agility = agi;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getWillpower()
	 */
	@Override
	public int getWillpower() {
		return willpower;
	}

	@Override
	public void setWillpower(int willpower) {
		if (willpower < 1) {
			willpower = 1;
		}

		this.willpower = willpower;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.domain.IStatusPoints#getDexterity()
	 */
	@Override
	public int getDexterity() {
		return dexterity;
	}

	@Override
	public void setDexterity(int dexterity) {
		if (dexterity < 1) {
			dexterity = 1;
		}

		this.dexterity = dexterity;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.StatusPoints#add(net.bestia.model.domain.
	 * StatusPoints)
	 */

	public void add(StatusPoints rhs) {

		this.maxHp += rhs.getMaxHp();
		this.maxMana += rhs.getMaxMana();
		this.currentHp += rhs.getCurrentHp();
		this.currentMana += rhs.getCurrentMana();

		this.strength += rhs.getStrength();
		this.vitality += rhs.getVitality();
		this.intelligence += rhs.getIntelligence();
		this.agility += rhs.getAgility();
		this.dexterity += rhs.getDexterity();
		this.willpower += rhs.getWillpower();
		this.magicDefense += rhs.getMagicDefense();
		this.defense += rhs.getDefense();
	}

	@Override
	public String toString() {
		return String.format("SP[curHp: %d, maxHp: %d, curMana: %d, maxMana: %d, str: %d vit: %d, int: %d,"
				+ " will: %d, agi: %d, dex: %d, def: %d, mdef: %d]",
				currentHp,
				maxHp,
				currentMana,
				maxMana,
				strength,
				vitality,
				intelligence,
				willpower,
				agility,
				dexterity,
				defense,
				magicDefense);
	}

	/**
	 * This will add or subtract HP from the current HP (depending if the
	 * argument is positive or negative). Will return TRUE if this does NOT
	 * lower the current HP below 1. FALSE otherwise.
	 * 
	 * @param addHp
	 *            The value to subtract from current mana value. Must be
	 *            positive.
	 * @return TRUE if the value could be lowered without hitting a negative
	 *         total HP value. FALSE otherwise.
	 */
	public boolean addHp(int addHp) {

		int curHp = getCurrentHp();
		setCurrentHp(getCurrentHp() + addHp);

		if (curHp + addHp > 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This will add or subtract Mana from the current Mana (depending if the
	 * argument is positive or negative). Will return TRUE if this does NOT
	 * lower the current Mana below 1. FALSE otherwise.
	 * 
	 * @param addMana
	 *            The value to subtract from current mana value. Must be
	 *            positive.
	 * @return TRUE if the value could be lowered without hitting a negative
	 *         total Mana value. FALSE otherwise.
	 */
	public boolean addMana(int addMana) {
		int curMana = getCurrentMana();
		setCurrentMana(getCurrentHp() + addMana);

		if (curMana + addMana > 0) {
			return true;
		} else {
			return false;
		}
	}
}
