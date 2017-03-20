package net.bestia.zoneserver.entity.ecs;

import java.io.Serializable;

import net.bestia.zoneserver.entity.EntityContext;

public class Entity implements Serializable {

	private static final long serialVersionUID = 1L;
	private long id = -1;
	
	/**
	 * This will set an id of -1.
	 */
	public Entity(long id) {
		
		this.id = id;
	}
	
	public long getId() {
		return id;
	}
}
