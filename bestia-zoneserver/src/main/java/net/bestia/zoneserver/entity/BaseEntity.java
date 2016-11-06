package net.bestia.zoneserver.entity;

import net.bestia.zoneserver.entity.traits.IdEntity;

/**
 * The base entity is the most simply form of an entity inside the bestia
 * system. It can be persisted inside the cache system but does not very much
 * serve any purpose thus this class is abstract.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BaseEntity implements IdEntity {

	private static final long serialVersionUID = 1L;
	private long id;

	public BaseEntity() {
		// no op.
	}

	/**
	 * Sets the ID of the entity. Ids can only be positive.
	 * 
	 * @param id
	 *            The new ID of this entity.
	 */
	public void setId(long id) {
		if (id < 0) {
			throw new IllegalArgumentException("Ids can only be positive.");
		}
		this.id = id;
	}

	/**
	 * The id of this entity.
	 */
	@Override
	public long getId() {
		return id;
	}

}
