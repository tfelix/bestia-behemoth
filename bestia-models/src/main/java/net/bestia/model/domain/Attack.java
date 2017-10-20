package net.bestia.model.domain;

public interface Attack {

	/**
	 * Basic attack id used for the default attack every bestia has. Each bestia
	 * has the default melee or ranged attack.
	 */
	public final static int DEFAULT_MELEE_ATTACK_ID = -1;

	public final static int DEFAULT_RANGE_ATTACK_ID = -2;

	/**
	 * @return ID of the attack.
	 */
	int getId();

	/**
	 * @return Information on which target the attack is directed.
	 */
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
	 * @return TRUE if there is a script which can get called when executing
	 *         this attack.
	 */
	boolean hasScript();

	/**
	 * Returns if the attack requires a line of sight.
	 * 
	 * @return TRUE of the attack requires a line of sight to the enemy. FALSE
	 *         otherwise.
	 */
	boolean needsLineOfSight();

	/**
	 * Base cast time of an attack in ms.
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

	/**
	 * The indicator description used to build the indicator for this attack.
	 * 
	 * @return Indicator name.
	 */
	String getIndicator();

	/**
	 * Returns the type of attack.
	 * 
	 * @return
	 */
	AttackType getType();

	/**
	 * @return TRUE if the attack is ranged or FALSE if its melee.
	 */
	default boolean isRanged() {
		return getType() == AttackType.RANGED_MAGIC || getType() == AttackType.RANGED_PHYSICAL;
	}
	
	/**
	 * @return TRUE if the attack is magic or FALSE if its physical.
	 */
	default boolean isMagic() {
		return getType() == AttackType.RANGED_MAGIC || getType() == AttackType.MELEE_MAGIC;
	}
}