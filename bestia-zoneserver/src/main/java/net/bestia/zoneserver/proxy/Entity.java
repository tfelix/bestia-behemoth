package net.bestia.zoneserver.proxy;

import java.util.Collection;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Damage;

public interface Entity {

	StatusPoints getStatusPoints();

	Location getLocation();

	/**
	 * Sets the current facing direction of the entity.
	 * 
	 * @return The current facing direction.
	 */
	Direction getFacing();

	/**
	 * The entity ID representing this entity in the syste.
	 * 
	 * @return The entity ID.
	 */
	int getEntityId();

	/**
	 * Apply this damage to the entity. Usually status effects or equipments
	 * might alter the real damage taken. It is also possible that the damage
	 * object itself will be altered (changed to a miss if the damage was
	 * avoided totally for example).
	 * 
	 * @param dmg
	 */
	void takeDamage(Damage dmg);

	/**
	 * Requests to use the attack. There will be no calculation of damage
	 * whatsoever. This method ONLY asks the entity to use this attack. If the
	 * entity knows this attack and also fullfills the requirements to use it
	 * (most notably has enough mana to do so) the side effects will be given to
	 * this entity (mana substraction, use of items etc.).
	 * 
	 * @param atk
	 * @return If the attack was successful used a TRUE will be returned FALSE
	 *         otherwise.
	 */
	boolean useAttack(Attack atk);

	/**
	 * Destroys/Kills the entity.
	 */
	void kill();

	/**
	 * Gets a list of all possible attacks used by this entity.
	 * 
	 * @return
	 */
	Collection<Attack> getAttacks();

	/**
	 * Returns the remaining cooldown of the given attack ID. If the attack id
	 * is unknown -1 will be returned as cooldown.
	 * 
	 * @param attackId
	 *            The attack ID of which the cooldown is requested.
	 * @return The remaining cooldown of the attack in ms or -1 if the attack is
	 *         unknown.
	 */
	int getRemainingCooldown(int attackId);

	/**
	 * Triggers the cooldown for the given attack id.
	 * 
	 * @param attackId
	 */
	void triggerCooldown(int attackId);

}