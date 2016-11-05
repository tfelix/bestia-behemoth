package net.bestia.zoneserver.entity.traits;

/**
 * Makes entities identifiably. Base interface for all entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface IdEntity {

	/**
	 * Returns the unique ID for each entity.
	 * 
	 * @return The unique ID for each entity.
	 */
	long getId();

}
