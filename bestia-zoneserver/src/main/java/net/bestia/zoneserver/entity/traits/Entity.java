package net.bestia.zoneserver.entity.traits;

import java.io.Serializable;

import net.bestia.zoneserver.entity.EntityAkkaContext;
import net.bestia.zoneserver.entity.EntityContext;

/**
 * Makes entities identifiably. Base interface for all entities. Must be
 * serializable because the entites will be saved by hazelcast.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Entity extends Serializable {

	/**
	 * Returns the unique ID for each entity.
	 * 
	 * @return The unique ID for each entity.
	 */
	long getId();

	/**
	 * Sets the unique id for this entity.
	 * 
	 * @param id
	 *            The entity id.
	 */
	void setId(long id);

	/**
	 * Sets the {@link EntityAkkaContext} for this entity.
	 * 
	 * @param ctx
	 *            The entity context.
	 */
	void setEntityContext(EntityContext ctx);
}
