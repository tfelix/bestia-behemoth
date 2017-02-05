package net.bestia.model.entity;

public interface IStatusBasedValues {

	/**
	 * Sets the level of the modifier.
	 * 
	 * @param level The new level.
	 */
	void setLevel(int level);

	/**
	 * Re-calculates the current HP and mana regeneration rate based on stats.
	 */
	float getHpRegenRate();

	float getManaRegenRate();

	/**
	 * Denotes the chance of a critical hit. Hit is a fixed point with 1/10
	 * increments from 0 to 1000. (1000 means 100% crit chance).
	 * 
	 * @return Critical hit percentage between 0 and 100% (in 1/10 increments,
	 *         thus 0 to 1000).
	 */
	int getCriticalHitrate();

	/**
	 * Chance of dodging an enemy attack. This only applies for physical attacks
	 * since magic can not be dodged. Fixed point value between 0 and 1000 (1/10
	 * increments).
	 * 
	 * @return Dodge percentage between 0 and 100% (in 1/10 increments, thus 0
	 *         to 1000).
	 */
	int getDodge();

	float getCasttime();

	float getCastduration();

	int getWillpowerResistance();

	int getVitalityResistance();

	int getHitrate();

	int getMinDamage();

	int getRangedBonusDamage();

	int getAttackSpeed();

	int getWalkspeed();

}