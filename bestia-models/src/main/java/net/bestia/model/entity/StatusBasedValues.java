package net.bestia.model.entity;

import java.util.Objects;

import net.bestia.model.domain.StatusPoints;

/**
 * These modifier are calculates based on status values. The are used to
 * calculate various aspects of the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusBasedValues {

	private final StatusPoints status;
	private int level;

	public StatusBasedValues(StatusPoints status, int level) {
		if (level < 1) {
			throw new IllegalArgumentException("Level can not be smaller then 1.");
		}

		this.status = Objects.requireNonNull(status);
		this.level = level;
	}

	/**
	 * Sets the level of the modifier.
	 * 
	 * @param level The new level.
	 */
	public void setLevel(int level) {
		this.level = level;
	}

	/**
	 * Re-calculates the current HP and mana regeneration rate based on stats.
	 */
	public float getHpRegenRate() {

		final float hpRegen = (status.getVitality() * 4 + level) / 100.0f;
		return hpRegen;
	}

	public float getManaRegenRate() {

		final float manaRegen = (status.getVitality() * 1.5f + level) / 100.0f;
		return manaRegen;
	}

	/**
	 * Denotes the chance of a critical hit. Hit is a fixed point with 1/10
	 * increments from 0 to 1000. (1000 means 100% crit chance).
	 * 
	 * @return Critical hit percentage between 0 and 100% (in 1/10 increments,
	 *         thus 0 to 1000).
	 */
	public int getCriticalHitrate() {
		return 0;
	}

	/**
	 * Chance of dodging an enemy attack. This only applies for physical attacks
	 * since magic can not be dodged. Fixed point value between 0 and 1000 (1/10
	 * increments).
	 * 
	 * @return Dodge percentage between 0 and 100% (in 1/10 increments, thus 0
	 *         to 1000).
	 */
	public int getDodge() {
		return 0;
	}

	public float getCasttime() {
		return 1;
	}

	public float getCastduration() {
		return 1;
	}

	public int getWillpowerResistance() {
		return 0;
	}

	public int getVitalityResistance() {
		return 0;
	}

	public int getHitrate() {
		return 1000;
	}

	public int getMinDamage() {
		return 60;
	}

	public int getRangedBonusDamage() {
		return 0;
	}

	public int getAttackSpeed() {
		return 0;
	}

	public int getWalkspeed() {
		return 100;
	}
}
