package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Collision;
import net.bestia.zoneserver.ecs.component.Script;
import net.bestia.zoneserver.zone.shape.CollisionShape;

/**
 * This system will check if any entities are colliding with scripts. If this is
 * the case the scripts will be executed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ScriptCollisionSystem extends EntityProcessingSystem {
	
	private ComponentMapper<Collision> collisionMapper;
	//private ComponentMapper<Script> scriptMapper;
	//private ComponentMapper<Position> positionMapper;
	
	/**
	 * Subscribe to all entities candidate for script execution.
	 */
	private EntitySubscription collidableEntitySubscription;
	

	public ScriptCollisionSystem() {
		super(Aspect.all(Collision.class, Script.class));
		// no op.
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		final AspectSubscriptionManager asm = world.getManager(AspectSubscriptionManager.class);
		collidableEntitySubscription = asm.get(Aspect.all(Bestia.class, Collision.class));
	}

	@Override
	protected void process(Entity e) {
		
		// Tick through all scripts and see if something is colliding with it.
		final CollisionShape scriptShape = collisionMapper.get(e).shape;
		
		// Currently we are iterating through ALL entities. This must be optimized via a quadtree.
		IntBag possibleEntities = collidableEntitySubscription.getEntities();
		
		for (int i = 0; i < possibleEntities.size(); i++) {
			final Entity visibleEntity = world.getEntity(possibleEntities.get(i));
			final CollisionShape shape = collisionMapper.get(visibleEntity).shape;
			
			if(!scriptShape.collide(shape)) {
				continue;
			}
			
			// Execute script logic.
			
		}
		
	}

}
