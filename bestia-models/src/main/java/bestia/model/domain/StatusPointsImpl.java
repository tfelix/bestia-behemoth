package bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix
 *
 */
@Embeddable
public class StatusPointsImpl implements Serializable, StatusPoints {

	private static final long serialVersionUID = 1L;

	private int strength = 1;
	private int vitality = 1;
	private int intelligence = 1;
	private int willpower = 1;
	private int agility = 1;
	private int dexterity = 1;
	private int physicalDefense = 0;
	private int magicDefense = 0;

	public StatusPointsImpl() {
		// no op.
	}

	public StatusPointsImpl(StatusPoints rhs) {

		strength = rhs.getStrength();
		vitality = rhs.getVitality();
		intelligence = rhs.getIntelligence();
		willpower = rhs.getWillpower();
		agility = rhs.getAgility();
		dexterity = rhs.getDexterity();
		physicalDefense = rhs.getDefense();
		magicDefense = rhs.getMagicDefense();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IStatusPoints#getDefense()
	 */
	@Override
	public int getDefense() {
		return physicalDefense;
	}

	@Override
	public void setDefense(int def) {
		if (def < 0) {
			def = 0;
		}
		if (def > 1000) {
			def = 1000;
		}

		this.physicalDefense = def;
	}

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	/*
	 * (non-Javadoc)
	 * 
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

	@Override
	public void set(StatusPoints rhs) {

		this.agility = rhs.getAgility();
		this.physicalDefense = rhs.getDefense();
		this.dexterity = rhs.getDexterity();
		this.intelligence = rhs.getIntelligence();
		this.magicDefense = rhs.getMagicDefense();
		this.strength = rhs.getStrength();
		this.vitality = rhs.getVitality();
		this.willpower = rhs.getWillpower();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.StatusPoints#add(net.bestia.model.domain.
	 * StatusPoints)
	 */

	public void add(StatusPoints rhs) {
		this.strength += rhs.getStrength();
		this.vitality += rhs.getVitality();
		this.intelligence += rhs.getIntelligence();
		this.agility += rhs.getAgility();
		this.dexterity += rhs.getDexterity();
		this.willpower += rhs.getWillpower();
		this.magicDefense += rhs.getMagicDefense();
		this.physicalDefense += rhs.getDefense();
	}

	@Override
	public int hashCode() {
		return Objects.hash(agility, physicalDefense, dexterity, intelligence, magicDefense, strength, vitality,
				willpower);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		
		
		
		if (!(obj instanceof StatusPoints)) {
			return false;
		}
		StatusPoints other = (StatusPoints) obj;
		
		return Objects.equals(agility, other.getAgility()) && 
				Objects.equals(physicalDefense, other.getDefense()) &&
				Objects.equals(dexterity, other.getDexterity()) &&
				Objects.equals(intelligence, other.getIntelligence()) &&
				Objects.equals(magicDefense, other.getMagicDefense()) &&
				Objects.equals(strength, other.getStrength()) &&
				Objects.equals(vitality, other.getVitality()) &&
				Objects.equals(willpower, other.getWillpower());
	}

	@Override
	public String toString() {
		return String.format("SP[maxHp: %d, maxMana: %d, str: %d vit: %d, int: %d,"
				+ " will: %d, agi: %d, dex: %d, def: %d, mdef: %d]",
				strength,
				vitality,
				intelligence,
				willpower,
				agility,
				dexterity,
				physicalDefense,
				magicDefense);
	}
}
