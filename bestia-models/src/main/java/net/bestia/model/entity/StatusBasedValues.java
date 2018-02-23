package net.bestia.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.domain.StatusPoints;
import net.bestia.model.map.Walkspeed;

/**
 * The {@link StatusBasedValues} are used for advanced calculations. They are
 * usually based upon {@link StatusPoints} and provide data needed for further
 * algorithms into the game. This implementation can be wrapped/decorated in
 * order to change the values based on modifier.
 * 
 * @author Thomas Felix
 *
 */
public interface StatusBasedValues {

	/**
	 * Sets the level of the modifier.
	 * 
	 * @param level
	 *            The new level.
	 */
	void setLevel(int level);

	/**
	 * @return Returns the current HP regeneration per second.
	 */
	@JsonProperty("hpr")
	float getHpRegenRate();

	/**
	 * @return The current mana regeneration per second.
	 */
	@JsonProperty("manar")
	float getManaRegenRate();

	/**
	 * Denotes the chance of a critical hit. Hit is a fixed point with 1/10
	 * increments from 0 to 1000. (1000 means 100% crit chance).
	 * 
	 * @return Critical hit percentage between 0 and 100% (in 1/10 increments,
	 *         thus 0 to 1000).
	 */
	@JsonProperty("cr")
	int getCriticalHitrate();

	/**
	 * Chance of dodging an enemy attack. This only applies for physical attacks
	 * since magic can not be dodged. Fixed point value between 0 and 1000 (1/10
	 * increments).
	 * 
	 * @return Dodge percentage between 0 and 100% (in 1/10 increments, thus 0
	 *         to 1000).
	 */
	@JsonProperty("d")
	int getDodge();

	@JsonProperty("ct")
	float getCasttimeMod();

	@JsonProperty("cd")
	float getCooldownMod();

	/**
	 * Modifier to denote how LONG the spells incantation will last once they
	 * have been casted. This does not apply to all spells. One shot damage
	 * spells most likly wont benefit from this modifier but enchantments which
	 * will persist for a certain amount of time into the world will certainly
	 * do.
	 * 
	 * @return A modifier of the cast duration.
	 */
	@JsonProperty("cdu")
	float getSpellDurationMod();

	@JsonProperty("hr")
	int getHitrate();

	@JsonIgnore
	int getMinDamage();

	@JsonIgnore
	int getRangedBonusDamage();

	/**
	 * This returns the attacks per second. This is only used for basic attacks
	 * since skill attacks usually have their own cooldown timer.
	 * 
	 * @return
	 */
	@JsonProperty("aspd")
	float getAttackSpeed();

	@JsonProperty("w")
	Walkspeed getWalkspeedMod();
}