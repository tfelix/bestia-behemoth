package net.bestia.zoneserver.actor;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.NPCEntity;
import net.bestia.zoneserver.entity.ecs.EcsEntityService;
import net.bestia.zoneserver.entity.factory.EntityFactory;
import net.bestia.zoneserver.entity.traits.Entity;

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
	
	public static final String NAME = "spawnHelper";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final EcsEntityService entityService;
	
	private final int NUM_NPCS = 5;
	
	private EntityFactory fac;
	
	@Autowired
	public SpawnActorHelper(EntityFactory fac, EcsEntityService service) {
		super(5000);
		
		this.fac = fac;
		this.entityService = service;
		
		setIntervalDuration(100000);
	}

	@Override
	protected void onTick() {
		
		Collection<Entity> entities = entityService.getEntitiesInRange(new Rect(0,0,100,100));
		
		int found = 0;
		

		for(Entity e : entities) {
			if(e instanceof NPCEntity) {
				found++;
			}
		}
		
		LOG.debug("Spawning {} bestias.", NUM_NPCS - found);
		
		for(int i = found; i < NUM_NPCS; i++) {
			
			fac.spawnBestia("blob", 10 + i, 10 + i);
		}
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		// no op.
	}

}
