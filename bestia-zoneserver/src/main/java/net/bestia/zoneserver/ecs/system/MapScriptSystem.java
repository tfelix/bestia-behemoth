package net.bestia.zoneserver.ecs.system;

import java.util.HashSet;
import java.util.Set;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Collision;
import net.bestia.zoneserver.ecs.component.TriggerScript;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.script.MapTriggerScript;
import net.bestia.zoneserver.zone.shape.CollisionShape;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

/**
 * System will check for TriggerScripts which are currently triggered by
 * entities who are touching them. All the needed event trigger are executed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MapScriptSystem extends EntityProcessingSystem {

	private EntitySubscription collidableEntitySubscription;

	private ComponentMapper<TriggerScript> triggerMapper;
	private ComponentMapper<Collision> collisionMapper;
	private ComponentMapper<Bestia> bestiaMapper;

	public MapScriptSystem() {
		super(Aspect.all(Collision.class, TriggerScript.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		final AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
		collidableEntitySubscription = asm.get(Aspect.all(Bestia.class, Collision.class));
	}

	@Override
	protected void process(Entity e) {

		final TriggerScript scriptComp = triggerMapper.get(e);
		final MapTriggerScript script = scriptComp.script;
		final CollisionShape scriptShape = collisionMapper.get(e).shape;

		// Usually we would use our bounding box and a manager (?) to find all
		// possible colliding entities.
		// manager.findPossibleColliding(shape.getBoundingBox());

		// Now we check agains ALL entities on the map. Because reasons.
		final IntBag possibleCollisions = collidableEntitySubscription.getEntities();
		final Set<Integer> newCollisions = new HashSet<>();
		for (int i = 0; i < possibleCollisions.size(); i++) {
			final Entity collisionEntity = world.getEntity(possibleCollisions.get(i));
			final CollisionShape entityShape = collisionMapper.get(collisionEntity).shape;

			if (!entityShape.collide(scriptShape)) {
				continue;
			}

			// Collision occured. Check if we entered of have left the script.
			if (scriptComp.lastTriggeredEntities.contains(collisionEntity.getId())) {
				// We still touch the script.
				final BestiaManager bm = bestiaMapper.get(collisionEntity).bestiaManager;
				script.onInside(bm);
			} else {
				// We are newly touching/entering it.
				final BestiaManager bm = bestiaMapper.get(collisionEntity).bestiaManager;
				script.onEnter(bm);
				newCollisions.add(collisionEntity.getId());
			}
		}

		// Trigger the onExit method for all entities not inside the script zone
		// anymore.
		scriptComp.lastTriggeredEntities.removeAll(newCollisions);
		for (Integer id : scriptComp.lastTriggeredEntities) {
			final Entity exitEntity = world.getEntity(id);
			if (exitEntity == null) {
				// Entity was deleted. Nevermind its gone.
				continue;
			}
			final BestiaManager bm = bestiaMapper.get(exitEntity).bestiaManager;
			script.onExit(bm);
		}

		// Reset the current entities of the script.
		scriptComp.lastTriggeredEntities.clear();
		scriptComp.lastTriggeredEntities.addAll(newCollisions);
	}

}
