package net.bestia.zoneserver.actor.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.BestiaPeriodicActor;
import net.bestia.zoneserver.entity.traits.Entity;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.service.EntityService;

@Component
@Scope("prototype")
public class EntityAiActor extends BestiaPeriodicActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final long entityId;
	private final EntityService entityService;
	
	@Autowired
	public EntityAiActor(EntityService entityService, long entityId) {
		super(5000);
		
		this.entityId = entityId;
		this.entityService = entityService;
		
		setIntervalDuration(3000);
	}

	@Override
	protected void onTick() {
		
		// See if its a movable entity.
		Entity e = entityService.getEntity(entityId);
		
		if(!(e instanceof Locatable)) {
			return;
		}
		
		Locatable me = (Locatable) e;
		
		LOG.debug("Entity {} moving.", entityId);
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		// no op.
	}
}
