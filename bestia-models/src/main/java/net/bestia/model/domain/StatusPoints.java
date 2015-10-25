package net.bestia.model.domain;

public interface StatusPoints {

	int getCurrentHp();

	void setCurrentHp(int hp);

	int getMaxHp();

	void setMaxHp(int maxHp);

	int getCurrentMana();

	void setCurrentMana(int mana);

	void setMaxMana(int maxMana);

	int getMaxMana();

	int getArmorDef();

	void setArmorDef(int armorDef);

	int getArmorSpDef();

	void setArmorSpDef(int armorSpDef);

	int getAtk();

	void setAtk(int atk);

	int getDef();

	void setDef(int def);

	int getSpAtk();

	void setSpAtk(int spAtk);

	int getSpDef();

	void setSpDef(int spDef);

	int getSpd();

	void setSpd(int spd);

	/**
	 * Special setter to avoid the clearance of any of the current mana or
	 * current hp value wich will occure if one set a single value but the other
	 * value has not yet been set. Either one of the values will get reset. To
	 * avoid this use this method and set the limiting values at the same time.
	 * 
	 * @param maxHp
	 * @param maxMana
	 */
	void setMaxValues(int maxHp, int maxMana);

	/**
	 * Adds some other status points to this object.
	 * 
	 * @param rhs
	 */
	void add(StatusPoints rhs);

}