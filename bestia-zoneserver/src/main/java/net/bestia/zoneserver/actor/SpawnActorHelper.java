package net.bestia.zoneserver.actor;

import java.util.Collection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.entity.EntitySpawnMessage;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.domain.VisualType;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.EntityContext;
import net.bestia.zoneserver.entity.NPCEntity;
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
	
	public static final String NAME = "spawnHelper";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final EntityService entityService;
	
	private final int NUM_NPCS = 5;
	
	private ActorSelection managerActor;
	private EntityContext ctx;
	
	public SpawnActorHelper(EntityService entityService, EntityContext ctx) {
		super(5000);
		
		this.entityService = entityService;
		this.ctx = ctx;
		
		setIntervalDuration(100000);
		
		managerActor = getContext().actorSelection("/user/behemoth/entities");
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
			BaseValues bv = BaseValues.getNewIndividualValues();
			SpriteInfo si = new SpriteInfo("poring", VisualType.PACK);
			
			// Save first for ID.
			NPCEntity be = new NPCEntity(bv, bv, bv, si);
			be.setEntityContext(ctx);
			entityService.save(be);
			
			
			// Then position.
			be.setPosition(10 + i, 20 + i);
			entityService.save(be);
			
			//EntitySpawnMessage msg = new EntitySpawnMessage(be.getId());
			//managerActor.tell(msg, getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		// no op.
	}

}
