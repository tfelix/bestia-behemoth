package net.bestia.zoneserver.manager;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;

public interface PlayerBestiaManagerInterface {

	int MAX_ATK_SLOTS = 5;

	/**
	 * Adds a certain amount of experience to the bestia. After this it checks
	 * if a levelup has occured. Experience must be positive.
	 * 
	 * @param exp
	 *            Experience to be added.
	 */
	void addExp(int exp);

	/**
	 * Returns the maximum item weight the current bestia could carry. Plase
	 * note: only the bestia master will be used to calculate the inventory max
	 * weight.
	 * 
	 * @return
	 */
	int getMaxItemWeight();

	/**
	 * Calculates and sets the status values to the PlayerBestia connected with
	 * this manager. This is handy if the current status values must be send to
	 * the client.
	 */
	void updateStatusValues();

	/**
	 * Returns the domain models controlled by this manager. Please use this
	 * only to persist it to databse or for reading access since sync with the
	 * ECS might get compromised otherwise.
	 * 
	 * @return The managed {@link PlayerBestia} domain model.
	 */
	PlayerBestia getPlayerBestia();

	/**
	 * Shortcut to get the id of the wrapped bestia.
	 * 
	 * @return The id of the wrapped bestia.
	 */
	int getPlayerBestiaId();

	long getAccountId();

	StatusPoints getStatusPoints();

	Location getLocation();

	int getLevel();

	void setAttack(int slot, Attack atk);

	/**
	 * Uses an attack in a given slot. If the slot is not set then it will do
	 * nothing. Also if the requisites for execution of an attack (mana,
	 * cooldown etc.) are not met there will also be no effect. If the slot
	 * number is invalid an {@link IllegalArgumentException} will be thrown.
	 * Slot numbering starts with 0 for the first slot.
	 * 
	 * @param slot
	 *            Number of the slot attack to be used. Starts with 0.
	 */
	boolean useAttackInSlot(int slot);

	/**
	 * Returns the attack for a given slot number.
	 * 
	 * @param slot
	 * @return The {@link Attack} in the given slot or {@code NULL} if no attack
	 *         was saved in this slot.
	 */
	Attack getAttackInSlot(int slot);

}