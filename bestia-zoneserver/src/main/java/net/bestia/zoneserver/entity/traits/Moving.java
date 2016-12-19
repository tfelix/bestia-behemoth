package net.bestia.zoneserver.entity.traits;

/**
 * An entity which is capable of moving. It must have an position but
 * additionally it will return a movement speed (which might be dependend on
 * equiptment/status effects etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Moving extends Locatable {

	/**
	 * Returns the current movement speed. 1 is the nominal speed which is 1.4
	 * m/s (or 1.4 tiles per second). The minimum speed returned via this method
	 * is 0.01 otherwise it is clamped to 0 (which means the unit can no longer
	 * move). The upper limit is 10.
	 * 
	 * @return The current movement speed of the unit.
	 */
	float getMovementSpeed();

}
