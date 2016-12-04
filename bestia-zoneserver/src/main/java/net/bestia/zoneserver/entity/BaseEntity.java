package net.bestia.zoneserver.entity;

import java.util.Objects;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.entity.traits.IdEntity;
import net.bestia.zoneserver.entity.traits.Locatable;

/**
 * The base entity is the most simply form of an entity inside the bestia
 * system. It can be persisted inside the cache system but does not very much
 * serve any purpose thus this class is abstract.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BaseEntity implements IdEntity, Locatable {

	private static final long serialVersionUID = 1L;

	protected final EntityContext ctx;
	private long id;

	public BaseEntity(EntityContext ctx) {

		this.ctx = Objects.requireNonNull(ctx);
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

	public long getX() {
		return getPosition().getX();
	}

	public long getY() {
		return getPosition().getY();
	}

	/**
	 * Returns the sight range of the entity. This method is very important,
	 * because it is used in case of a change to determine all clients which are
	 * in range and must be updated about the change.
	 * 
	 * @return The range {@link Rect} of the sight range originating from this
	 *         entity.
	 */
	public Rect getSightRect() {
		final Point pos = getPosition();
		final Rect sightRect = new Rect(pos.getX() - Map.SIGHT_RANGE,
				pos.getY() - Map.SIGHT_RANGE,
				pos.getX() + Map.SIGHT_RANGE,
				pos.getY() + Map.SIGHT_RANGE);
		return sightRect;
	}

}
