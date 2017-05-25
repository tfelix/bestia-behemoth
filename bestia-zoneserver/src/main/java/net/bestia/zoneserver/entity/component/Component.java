package net.bestia.zoneserver.entity.component;

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

	public Component(long id) {
		this.id = id;
	}

	/**
	 * The unqiue id of the component.
	 * 
	 * @return The unique component id.
	 */
	public long getId() {
		return id;
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
