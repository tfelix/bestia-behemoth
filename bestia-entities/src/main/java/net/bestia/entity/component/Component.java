package net.bestia.entity.component;

import java.io.Serializable;

/**
 * Each component needs a unique ID because this is required by the way
 * components are saved into the DHT of hazelcast. This is must be unique in the
 * whole system.
 * 
 * Components must have a ctor which accepts only an long id value.
 * 
 * @author Thomas Felix
 *
 */
public abstract class Component implements Serializable {

	private static final long serialVersionUID = 1L;

	private final long id;
	private long entityId;

	public Component(long id, long entityId) {

		this.id = id;
		this.entityId = entityId;
	}

	/**
	 * The unqiue id of the component.
	 * 
	 * @return The unique component id.
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the entity id to which this component is attached.
	 * 
	 * @return The entity id to which this component is attached.
	 */
	public long getEntityId() {
		return entityId;
	}

	/**
	 * Sets the entity id. Components can be reused and reattached to a new
	 * entity. Thus the ID need to be changable.
	 * 
	 * @param entityId
	 *            The new entity id.
	 */
	public void setEntityId(long entityId) {
		this.entityId = entityId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Component other = (Component) obj;
		if (id != other.id)
			return false;
		return true;
	}
}
