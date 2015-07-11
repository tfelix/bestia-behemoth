package net.bestia.zoneserver.ecs.event;

import com.artemis.Entity;

import net.mostlyoriginal.api.event.common.Event;

/**
 * Triggers an instant persistent process via the PersistSystem.
 * 
 * @author Thomas
 *
 */
public class PersistEvent implements Event {

	public final Entity entity;
	
	public PersistEvent(Entity e) {
		this.entity = e;
	}
	
}
