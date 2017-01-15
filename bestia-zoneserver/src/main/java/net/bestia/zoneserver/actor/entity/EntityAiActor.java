package net.bestia.zoneserver.actor.entity;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.entity.traits.Entity;
import net.bestia.zoneserver.entity.traits.Moving;
import net.bestia.zoneserver.service.EntityService;

public class EntityAiActor extends BestiaPeriodicActor {
	
	private final long entityId;
	private final EntityService entityService;
	
	@Autowired
	public EntityAiActor(EntityService entityService, Long entityId) {
		
		this.entityId = entityId;
		this.entityService = entityService;
		
		setIntervalDuration(3000);
	}

	@Override
	protected void onTick() {
		
		// See if its a movable entity.
		Entity e = entityService.getEntity(entityId);
		
		if(!(e instanceof Moving)) {
			return;
		}
		
		Moving me = (Moving) e;
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		// no op.
	}
}
