package net.bestia.core.game.model;

import javax.persistence.Embeddable;

/**
 * Status values for bestia entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class StatusPoints extends BaseValues {
	private int curHp;
	private int curMana;
	private int maxMana;
	private int maxHp;
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

	/**
	 * Sets the speed of the bestia. The speed determines walkspeed and
	 * spellcasting.
	 * 
	 * @param spd
	 */
	@Override
	public void setSpd(int spd) {
		super.setSpd(spd);
		checkInvalidStatusValue();
	}

	/**
	 * Adds some other status points to this object.
	 * 
	 * @param rhs
	 */
	public void add(StatusPoints rhs) {
		/*this.atk += rhs.getAtk();
		this.def += rhs.getDef();
		this.curHp += rhs.getCurrentHp();
		this.curMana += rhs.getCurrentMana();
		this.spAtk += rhs.getSpAtk();
		this.spd += rhs.getSpd();
		this.spDef += rhs.getSpDef();
		checkInvalidStatusValue();*/
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

		if (armorDef < 1) {
			armorDef = 1;
		}

		if (armorSpDef < 1) {
			armorSpDef = 1;
		}

	}
}
