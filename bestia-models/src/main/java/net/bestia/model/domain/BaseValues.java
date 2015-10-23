package net.bestia.model.domain;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.Embeddable;
import javax.persistence.Transient;

/**
 * Saves holds the basic bestia status values. Can be used to represent effort values or individual values.
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

	private int hp;
	private int mana;
	private int atk;
	private int def;
	private int spAtk;
	private int spDef;
	private int spd;

	public BaseValues() {
		hp = 0;
		mana = 0;
		atk = 0;
		def = 0;
		spAtk = 0;
		spDef = 0;
		spd = 0;
	}

	/**
	 * Creates a new BaseValues object with individual values set. Useful when generating a new bestia.
	 * 
	 * @return {@link BaseValues} instance initiated with random values between 0 and {@code MAX_IV_VALUE}.
	 */
	public static BaseValues getNewIndividualValues() {

		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		final BaseValues bv = new BaseValues();
		bv.setAtk(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setDef(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setHp(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setMana(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setSpAtk(rand.nextInt(0, MAX_IV_VALUE + 1));
		bv.setSpDef(rand.nextInt(0, MAX_IV_VALUE + 1));

		return bv;
	}

	/**
	 * To create non random starter bestia the values are all created equally.
	 * 
	 * @return {@link BaseValues} instance initiated with equal values of 13.
	 */
	public static BaseValues getStarterIndividualValues() {

		final BaseValues bv = new BaseValues();
		bv.setAtk(13);
		bv.setDef(13);
		bv.setHp(13);
		bv.setMana(13);
		bv.setSpAtk(13);
		bv.setSpDef(13);

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

	public int getAtk() {
		return atk;
	}

	public void setAtk(int atk) {
		if (atk < 0) {
			throw new IllegalArgumentException("Attack can not be negative.");
		}
		this.atk = atk;
	}

	public int getSpAtk() {
		return spAtk;
	}

	public void setSpAtk(int spAtk) {
		if (spAtk < 0) {
			throw new IllegalArgumentException("Special attack can not be negative.");
		}
		this.spAtk = spAtk;
	}

	public int getDef() {
		return def;
	}

	public void setDef(int def) {
		if (def < 0) {
			throw new IllegalArgumentException("Defense can not be negative.");
		}
		this.def = def;
	}

	public int getSpDef() {
		return spDef;
	}

	public void setSpDef(int spDef) {
		if (spDef < 0) {
			throw new IllegalArgumentException("Special defense can not be negative.");
		}
		this.spDef = spDef;
	}

	public int getSpd() {
		return spd;
	}

	public void setSpd(int spd) {
		if (spd < 0) {
			throw new IllegalArgumentException("Speed can not be negative.");
		}
		this.spd = spd;
	}
}
