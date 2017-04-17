package net.bestia.zoneserver.entity.ecs.components;

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

}
