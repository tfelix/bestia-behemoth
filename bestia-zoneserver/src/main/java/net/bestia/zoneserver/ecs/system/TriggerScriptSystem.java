package net.bestia.zoneserver.ecs.system;

import java.util.HashSet;
import java.util.Set;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Script;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.script.MapScript;
import net.bestia.zoneserver.script.MapScriptFactory;
import net.bestia.zoneserver.script.ScriptManager;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.shape.CollisionShape;

/**
 * System will check for TriggerScripts which are currently triggered by
 * entities who are touching them. All the needed event trigger are executed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class TriggerScriptSystem extends EntityProcessingSystem {

	private final static Logger LOG = LogManager.getLogger(TriggerScriptSystem.class);

	private EntitySubscription collidableEntitySubscription;

	private ComponentMapper<Script> triggerMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Position> positionMapper;

	private final Bindings onEnterBinding = new SimpleBindings();
	private final Bindings onExitBinding = new SimpleBindings();
	private final Bindings onInsideBinding = new SimpleBindings();

	private ScriptManager scriptManager;

	@Wire
	private Zone zone;

	@Wire
	private CommandContext ctx;

	@Wire
	private MapScriptFactory scriptFactory;

	private Archetype triggerScriptArchetype;

	public TriggerScriptSystem() {
		super(Aspect.all(Position.class, Script.class));

		// init the bindings.
		onEnterBinding.put("event", "onEnter");
		onExitBinding.put("event", "onExit");
		onInsideBinding.put("event", "onInside");
	}

	@Override
	protected void initialize() {
		super.initialize();

		final AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
		collidableEntitySubscription = asm.get(Aspect.all(Bestia.class, Position.class));

		triggerScriptArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(Script.class)
				.build(world);

		// Shortcut to the script manager.
		scriptManager = ctx.getScriptManager();
	}

	/**
	 * Creates a new trigger script.
	 * 
	 * @param name
	 * @param shape
	 * @return
	 */
	public int createTriggerScript(String name, CollisionShape shape) {
		final int id = world.create(triggerScriptArchetype);

		positionMapper.get(id).position = shape;
		triggerMapper.get(id).script = name;
		triggerMapper.get(id).lastTriggeredEntities.clear();

		LOG.debug("New script created: {}, on: {}", name, shape.toString());

		return id;
	}

	@Override
	protected void process(Entity e) {

		final Script script = triggerMapper.get(e);
		final CollisionShape scriptShape = positionMapper.get(e).position;
		final String zoneName = zone.getName();

		// TODO Das hier über den Quadtree lösen.
		// Usually we would use our bounding box and a manager (?) to find all
		// possible colliding entities.
		// manager.findPossibleColliding(shape.getBoundingBox());

		// Now we check agains ALL entities on the map. Because reasons.
		final IntBag possibleCollisions = collidableEntitySubscription.getEntities();

		final Set<Integer> newCollisions = new HashSet<>();

		for (int i = 0; i < possibleCollisions.size(); i++) {

			final Entity collisionEntity = world.getEntity(possibleCollisions.get(i));
			final CollisionShape entityShape = positionMapper.get(collisionEntity).position;

			if (!entityShape.collide(scriptShape)) {
				continue;
			}

			// Collision occured. Check if we entered of have left the script.
			if (script.lastTriggeredEntities.contains(collisionEntity.getId())) {
				// We still touch the script.
				final BestiaManager bm = bestiaMapper.get(collisionEntity).bestiaManager;
				onInsideBinding.put("target", bm);

				final MapScript mapScript = scriptFactory.getScript(script.script, bm);

				final boolean success = scriptManager.execute(mapScript, onInsideBinding);
				checkSuccess(e.getId(), success);
			} else {
				// We are newly touching/entering it.
				final BestiaManager bm = bestiaMapper.get(collisionEntity).bestiaManager;
				onEnterBinding.put("target", bm);

				final MapScript mapScript = scriptFactory.getScript(script.script, bm);
				
				final boolean success = scriptManager.execute(mapScript, onEnterBinding);
				checkSuccess(e.getId(), success);

				newCollisions.add(collisionEntity.getId());
			}
		}

		// Trigger the onExit method for all entities not inside the script zone
		// anymore.
		script.lastTriggeredEntities.removeAll(newCollisions);
		for (Integer id : script.lastTriggeredEntities) {
			final Entity exitEntity = world.getEntity(id);
			if (exitEntity == null) {
				// Entity was deleted. Nevermind its gone.
				continue;
			}
			final BestiaManager bm = bestiaMapper.get(exitEntity).bestiaManager;
			onExitBinding.put("target", bm);

			final MapScript mapScript = scriptFactory.getScript(script.script, bm);

			final boolean success = scriptManager.execute(mapScript, onExitBinding);
			checkSuccess(e.getId(), success);
		}

		// Reset the current entities of the script.
		script.lastTriggeredEntities.clear();
		script.lastTriggeredEntities.addAll(newCollisions);
	}

	/**
	 * If there was an error executing the script, remove the script from the
	 * ECS.
	 * 
	 * @param scriptId
	 * @param success
	 */
	private void checkSuccess(int scriptId, boolean success) {
		if (success) {
			return;
		}

		world.delete(scriptId);
	}

}
