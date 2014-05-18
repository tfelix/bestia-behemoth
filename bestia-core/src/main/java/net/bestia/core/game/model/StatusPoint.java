package net.bestia.core.game.model;

import javax.persistence.Embeddable;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class StatusPoint {
	private int curHp;
	private int curMana;
	private int maxMana;
	private int maxHp;
	private int atk;
	private int def;
	private int spAtk;
	private int spDef;
	private int spd;
	private int armorDef;
	private int armorSpDef;

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

	public void add(StatusPoint rhs) {
		this.atk += rhs.getAtk();
		this.def += rhs.getDef();
		this.curHp += rhs.getCurrentHp();
		this.curMana += rhs.getCurrentMana();
		this.spAtk += rhs.getSpAtk();
		this.spd += rhs.getSpd();
		this.spDef += rhs.getSpDef();
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

		/*
		 * Überprüft ob gewisse Statuswerte ihren max. Wert überschritten haben,
		 * wenn ja werden sie am max. gecapped. Normal ist dies HP und Mana.
		 */

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
		
		if(armorDef < 1) {
			armorDef = 1;
		}
		
		if(armorSpDef < 1) {
			armorSpDef = 1;
		}

		
	}
}
