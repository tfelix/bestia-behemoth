package net.bestia.zoneserver.entity.traits;

import java.io.Serializable;

/**
 * Makes entities identifiably. Base interface for all entities. Must be
 * serializable because the entites will be saved by hazelcast.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface IdEntity extends Serializable {

	/**
	 * Returns the unique ID for each entity.
	 * 
	 * @return The unique ID for each entity.
	 */
	long getId();

}
