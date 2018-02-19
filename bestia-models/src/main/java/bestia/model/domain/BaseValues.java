package bestia.model.domain;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

/**
 * Saves holds the basic bestia status values. Can be used to represent effort
 * values or individual values.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class BaseValues implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Transient
	private static final int MAX_IV_VALUE = 15;

	@Column(name = "bHp")
	private int hp;

	@Column(name = "bMana")
	private int mana;

	@Column(name = "bStr")
	private int strength;

	@Column(name = "bVit")
	private int vitality;

	@Column(name = "bInt")
	private int intelligence;

	@Column(name = "bWill")
	private int willpower;

	@Column(name = "bAgi")
	private int agility;
	
	@Column(name = "bDex")
	private int dexterity;

	public BaseValues() {
		hp = 0;
		mana = 0;
		strength = 0;
		vitality = 0;
		intelligence = 0;
		willpower = 0;
		agility = 0;
		dexterity = 0;
	}

	/**
	 * Creates a new BaseValues object with individual values set. Useful when
	 * generating a new bestia.
	 * 
	 * @return {@link BaseValues} instance initiated with random values between
	 *         0 and {@code MAX_IV_VALUE}.
	 */
	public static BaseValues getNewIndividualValues() {

		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		final BaseValues bv = new BaseValues();
		bv.setAttack(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setVitality(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setHp(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setMana(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setIntelligence(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setWillpower(rand.nextInt(0, MAX_IV_VALUE + 1));

		return bv;
	}

	/**
	 * To create non random starter bestia the values are all created equally.
	 * 
	 * @return {@link BaseValues} instance initiated with equal values of 13.
	 */
	public static BaseValues getStarterIndividualValues() {

		final BaseValues bv = new BaseValues();
		
		bv.setHp(13);
		bv.setMana(13);
		
		bv.setAttack(13);
		bv.setVitality(13);
		bv.setIntelligence(13);
		bv.setWillpower(13);
		bv.setDexterity(13);
		bv.setAgility(13);

		return bv;
	}

	/**
	 * All values are 0.
	 * 
	 * @return A {@link BaseValues} instance with all values set to 0.
	 */
	public static BaseValues getNullValues() {
		final BaseValues bv = new BaseValues();
		
		bv.setAttack(0);
		bv.setVitality(0);
		bv.setHp(0);
		bv.setMana(0);
		bv.setIntelligence(0);
		bv.setWillpower(0);
		bv.setDexterity(0);
		bv.setAgility(0);

		return bv;
	}

	public int getHp() {
		return hp;
	}

	public void setHp(int hp) {
		if (hp < 0) {
			throw new IllegalArgumentException("HP can not be negative.");
		}
		this.hp = hp;
	}

	public int getMana() {
		return mana;
	}

	public void setMana(int mana) {
		if (mana < 0) {
			throw new IllegalArgumentException("Mana can not be negative.");
		}
		this.mana = mana;
	}

	public int getAttack() {
		return strength;
	}

	public void setAttack(int atk) {
		if (atk < 0) {
			throw new IllegalArgumentException("Attack can not be negative.");
		}
		this.strength = atk;
	}

	public int getIntelligence() {
		return intelligence;
	}

	public void setIntelligence(int spAtk) {
		if (spAtk < 0) {
			throw new IllegalArgumentException("Special attack can not be negative.");
		}
		this.intelligence = spAtk;
	}

	public int getVitality() {
		return vitality;
	}

	public void setVitality(int def) {
		if (def < 0) {
			throw new IllegalArgumentException("Defense can not be negative.");
		}
		this.vitality = def;
	}

	public int getWillpower() {
		return willpower;
	}

	public void setWillpower(int spDef) {
		if (spDef < 0) {
			throw new IllegalArgumentException("Special defense can not be negative.");
		}
		this.willpower = spDef;
	}

	public int getAgility() {
		return agility;
	}

	public void setAgility(int agi) {
		if (agi < 0) {
			throw new IllegalArgumentException("Speed can not be negative.");
		}
		this.agility = agi;
	}
	
	public int getDexterity() {
		return dexterity;
	}
	
	public void setDexterity(int dexterity) {
		this.dexterity = dexterity;
	}
}
