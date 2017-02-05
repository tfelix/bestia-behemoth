package net.bestia.model.domain;

public interface StatusPoints {

	int getCurrentHp();

	int getMaxHp();

	int getCurrentMana();

	/**
	 * Returns the max mana.
	 * 
	 * @return Max mana.
	 */
	int getMaxMana();

	int getDefense();

	/**
	 * Returns the magic defense.
	 * 
	 * @return The magic defense.
	 */
	int getMagicDefense();

	/**
	 * Returns the strength.
	 * 
	 * @return
	 */
	int getStrength();

	int getVitality();

	/**
	 * Returns the intelligence.
	 * 
	 * @return Intelligence.
	 */
	int getIntelligence();

	/**
	 * Returns the agility of the entity.
	 * 
	 * @return The agility.
	 */
	int getAgility();

	int getWillpower();

	int getDexterity();

	void setDexterity(int dexterity);

	void setWillpower(int willpower);

	void setAgility(int agi);

	void setIntelligence(int intel);

	void setVitality(int vit);

	void setStrenght(int str);

	void setMagicDefense(int mdef);

	void setDefense(int def);

	void setMaxMana(int maxMana);

	void setCurrentMana(int mana);

	void setMaxHp(int maxHp);

	void setCurrentHp(int hp);

}