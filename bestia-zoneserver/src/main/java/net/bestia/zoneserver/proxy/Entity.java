package net.bestia.zoneserver.proxy;

import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Damage;

public interface Entity {

	StatusPoints getStatusPoints();

	Location getLocation();

	Direction getFacing();

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

}