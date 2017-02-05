package net.bestia.model.domain;

public interface Attack {

	AttackTarget getTarget();

	/**
	 * @return the attackDbName
	 */
	String getDatabaseName();

	/**
	 * @return the strength
	 */
	int getStrength();

	/**
	 * @return the element
	 */
	Element getElement();

	/**
	 * @return the manaCost
	 */
	int getManaCost();

	/**
	 * Returns if the attack requires a line of sight.
	 * 
	 * @return TRUE of the attack requires a line of sight to the enemy. FALSE
	 *         otherwise.
	 */
	boolean needsLineOfSight();

	/**
	 * @return the basedStatus
	 */
	AttackBasedStatus getBasedStatus();

	/**
	 * Base casttime of an attack in ms.
	 * 
	 * @return Cast time of an attack in ms.
	 */
	int getCasttime();

	/**
	 * Returns the range of the attack in tiles.
	 * 
	 * @return The range of the attack in tiles.
	 */
	int getRange();

	/**
	 * Base cooldown time after the attack can be used again in ms.
	 * 
	 * @return Cooldown of the attack in ms.
	 */
	int getCooldown();

	int getId();

	/**
	 * The indicator description used to build the indicator for this attack.
	 * 
	 * @return Indicator name.
	 */
	String getIndicator();

}