package net.bestia.model.battle;

public enum DamageType {
	
	/**
	 * Damage is used as heal.
	 */
	HEAL, 
	
	/**
	 * Damage was missed.
	 */
	MISS,

	/**
	 * Normal hit damage.
	 */
	HIT,

	/**
	 * This was a critical damage.
	 */
	CRITICAL,

	/**
	 * True damage will (in most cases) hit the bestia without modifications
	 * of status effects or equipments.
	 */
	TRUE
}