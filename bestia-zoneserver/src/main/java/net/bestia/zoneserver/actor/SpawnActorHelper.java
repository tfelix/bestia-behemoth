package net.bestia.zoneserver.actor;

import java.util.Collection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.EntitySpawnMessage;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.entity.EntityManagerActor;
import net.bestia.zoneserver.entity.traits.Entity;
import net.bestia.zoneserver.service.EntityService;

/**
 * This is just an actor which periodically checks if all bestia are spawned to
 * generate some entities.
 * 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class SpawnActorHelper extends BestiaPeriodicActor {
	
	private static final String NAME = "spawnHelper";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final EntityService entityService;
	
	private final int NUM_NPCS = 5;
	
	private ActorSelection managerActor;
	
	public SpawnActorHelper(EntityService entityService) {
		super(5000);
		
		this.entityService = entityService;
		
		setIntervalDuration(5000);
		
		managerActor = getContext().actorSelection("/user/behemoth/entities");
	}

	@Override
	protected void onTick() {
		
		Collection<Entity> entities = entityService.getEntitiesInRange(new Rect(0,0,100,100));
		
		int found = 0;
		
		/*
		for(Entity e : entities) {
			if(e instanceof BestiaEntity) {
				found++;
			}
		}
		
		LOG.debug("Spawning {} bestias.", NUM_NPCS - found);
		
		for(int i = found; i < NUM_NPCS; i++) {
			BaseValues bv = BaseValues.getNewIndividualValues();
			BestiaEntity be = new BestiaEntity(bv, bv, bv, "poring");
			entityService.save(be);
			
			EntitySpawnMessage msg = new EntitySpawnMessage(be.getId());
			managerActor.tell(msg, getSelf());
		}*/
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		// no op.
	}

}
