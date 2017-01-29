package net.bestia.zoneserver.entity;

import net.bestia.zoneserver.entity.traits.Entity;

/**
 * The base entity is the most simply form of an entity inside the bestia
 * system. It can be persisted inside the cache system but does not very much
 * serve any purpose thus this class is abstract.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BaseEntity implements Entity {

	private static final long serialVersionUID = 1L;

	private EntityContext ctx;
	private long id = -1;

	/**
	 * This will set an id of -1.
	 */
	public BaseEntity() {
		// no op.
	}

	/**
	 * Gets the {@link EntityContext}.
	 * 
	 * @return The context.
	 */
	protected EntityContext getContext() {
		if (ctx == null) {
			throw new IllegalStateException("Context is null. Please initialize a context with setEntityContext first.");
		}
		return ctx;
	}

	/**
	 * Sets the entity context for this entity. The context is needed to perform
	 * certain callbacks. It MUST be set before most calls can be made to the
	 * entity. But it must be removed upon saving the entity because it can not
	 * be serialized.
	 * 
	 * @param ctx
	 */
	@Override
	public void setEntityContext(EntityContext ctx) {

		this.ctx = ctx;
	}

	/**
	 * Sets the ID of the entity. Ids can only be positive or null. If the
	 * entity is first created and has no ID assigned it has the value -1.
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
