package net.bestia.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface StatusPoints {

	/**
	 * Defense against regular physical attacks.
	 * 
	 * @return Physical attack defense.
	 */
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
	 * Returns the physical strength.
	 * 
	 * @return Strength
	 */
	@JsonProperty("str")
	int getStrength();

	/**
	 * Returns the vitality.
	 * 
	 * @return Vitality.
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

}