package net.bestia.core.game.model;

import javax.persistence.Embeddable;

/**
 * Saves the effort values for a certain bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class EffortValues {
	private int hp;
	private int mana;
	private int atk;
	private int spAtk;
	private int spDef;
	private int spd;

	public EffortValues() {
		hp = 0;
		mana = 0;
		atk = 0;
		spAtk = 0;
		spDef = 0;
		spd = 0;		
	}

	public int getHp() {
		return hp;
	}

	public void setHp(int hp) {
		if(hp < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.hp = hp;
	}

	public int getMana() {
		return mana;
	}

	public void setMana(int mana) {
		if(mana < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.mana = mana;
	}

	public int getAtk() {
		return atk;
	}

	public void setAtk(int atk) {
		if(atk < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.atk = atk;
	}

	public int getSpAtk() {
		return spAtk;
	}

	public void setSpAtk(int spAtk) {
		if(spAtk < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.spAtk = spAtk;
	}

	public int getSpDef() {
		return spDef;
	}

	public void setSpDef(int spDef) {
		if(spDef < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.spDef = spDef;
	}

	public int getSpd() {
		return spd;
	}

	public void setSpd(int spd) {
		if(spd < 0) {
			throw new IllegalArgumentException(
					"Effortvalue can not be negative.");
		}
		this.spd = spd;
	}
	
	
}
