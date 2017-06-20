package net.bestia.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface StatusPoints {

	@JsonProperty("mhp")
	int getMaxHp();

	/**
	 * Returns the max mana.
	 * 
	 * @return Max mana.
	 */
	@JsonProperty("mmana")
	int getMaxMana();

	@JsonProperty("def")
	int getDefense();

	/**
	 * Returns the magic defense.
	 * 
	 * @return The magic defense.
	 */

	@JsonProperty("mdef")
	int getMagicDefense();

	/**
	 * Returns the strength.
	 * 
	 * @return
	 */
	@JsonProperty("str")
	int getStrength();

	/**
	 * Returns the vitality.
	 * 
	 * @return
	 */
	@JsonProperty("vit")
	int getVitality();

	/**
	 * Returns the intelligence.
	 * 
	 * @return Intelligence.
	 */
	@JsonProperty("int")
	int getIntelligence();

	/**
	 * Returns the agility of the entity.
	 * 
	 * @return The agility.
	 */
	@JsonProperty("agi")
	int getAgility();

	@JsonProperty("will")
	int getWillpower();

	@JsonProperty("dex")
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

	void setMaxHp(int maxHp);

}