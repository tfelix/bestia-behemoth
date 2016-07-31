package net.bestia.zoneserver.zone.entity;

import net.bestia.zoneserver.zone.Position;

/**
 * An entity which is capable of moving has the ability to manouver on its own.
 * 
 * @author Thomas
 *
 */
public interface Moving {

	/**
	 * Returns the current movement speed. 1 is the nominal speed which is 1.4
	 * m/s (or 1.4 tiles per second).
	 * 
	 * @return
	 */
	float getMovementSpeed();
	
	Position getPosition();
	void setPosition(long x, long y);

}
