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
import com.artemis.EntityTransmuter;
import com.artemis.EntityTransmuterFactory;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Delay;
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
public class ScriptEventSystem extends EntityProcessingSystem {

	private final static Logger LOG = LogManager.getLogger(ScriptEventSystem.class);

	private EntitySubscription collidableEntitySubscription;

	private ComponentMapper<Script> triggerMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Delay> delayMapper;

	private final Bindings onEnterBinding = new SimpleBindings();
	private final Bindings onExitBinding = new SimpleBindings();

	final Set<Integer> newCollisions = new HashSet<>();

	private ScriptManager scriptManager;

	@Wire
	private Zone zone;

	@Wire
	private CommandContext ctx;

	@Wire
	private MapScriptFactory scriptFactory;

	private Archetype eventScriptArch;
	private EntityTransmuter eventTickedTransmute;

	public ScriptEventSystem() {
		super(Aspect.all(Position.class, Script.class));

		// init the bindings.
		onEnterBinding.put("event", "onEnter");
		onExitBinding.put("event", "onExit");
	}

	@Override
	protected void initialize() {
		super.initialize();

		final AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
		collidableEntitySubscription = asm.get(Aspect.all(Bestia.class, Position.class));

		eventScriptArch = new ArchetypeBuilder()
				.add(Position.class)
				.add(Script.class)
				.build(world);

		eventTickedTransmute = new EntityTransmuterFactory(world)
				.add(Delay.class).build();

		// Shortcut to the script manager.
		scriptManager = ctx.getScriptManager();
	}

	/**
	 * Creates a new trigger event script without a tick rate. Because there is
	 * no tick rate the script will never tick for entities standing inside it.
	 * It will only fire upon enter and leave.
	 * 
	 * @param name
	 *            Name of the script to trigger upon enter/exit.
	 * @param shape
	 *            The area which is covered by the script.
	 * @return Id of the created entity.
	 */
	public int createTriggerScript(String name, CollisionShape shape) {
		final int id = world.create(eventScriptArch);

		positionMapper.get(id).position = shape;
		triggerMapper.get(id).script = name;
		triggerMapper.get(id).lastTriggeredEntities.clear();

		LOG.debug("New script created: {}, on: {}", name, shape.toString());

		return id;
	}

	/**
	 * Creates a new trigger event script with a tick rate. The tick rate will
	 * determine how often the script is fired when an entity is standing inside
	 * of it.
	 * 
	 * @param name
	 * @param shape
	 * @param tickRate
	 * @return
	 */
	public int createTriggerScript(String name, CollisionShape shape, int tickRate) {
		
		final int id = createTriggerScript(name, shape);
		
		eventTickedTransmute.transmute(id);
	
		delayMapper.get(id).setDelay(tickRate);
		
		LOG.debug("Added tickRate: {} on script {}", tickRate, name);

		return id;
	}

	@Override
	protected void process(Entity e) {

		newCollisions.clear();

		final Script script = triggerMapper.get(e);
		final CollisionShape scriptShape = positionMapper.get(e).position;
		final Set<Integer> lastCollisions = script.lastTriggeredEntities;

		// TODO Das hier über den Quadtree lösen.
		// Usually we would use our bounding box and a manager (?) to find all
		// possible colliding entities.
		// manager.findPossibleColliding(shape.getBoundingBox());

		// Now we check agains ALL entities on the map. Because reasons.
		final IntBag possibleCollisions = collidableEntitySubscription.getEntities();

		// Find all entities in this zone.
		for (int i = 0; i < possibleCollisions.size(); i++) {
			final Entity collisionEntity = world.getEntity(possibleCollisions.get(i));
			final CollisionShape entityShape = positionMapper.get(collisionEntity).position;

			if (!entityShape.collide(scriptShape)) {
				continue;
			}

			newCollisions.add(possibleCollisions.get(i));
		}

		// Now search for new collisions since the last time.
		newCollisions.stream()
				.filter(x -> !lastCollisions.contains(x))
				.forEach(id -> {
					// We are newly touching/entering it.
						final Entity collisionEntity = world.getEntity(id);
						final BestiaManager bm = bestiaMapper.get(collisionEntity).bestiaManager;

						onEnterBinding.put("target", bm);

						final MapScript mapScript = scriptFactory.getScript(script.script, bm);

						final boolean success = scriptManager.execute(mapScript, onEnterBinding);
						checkSuccess(e.getId(), success);
					});

		// Now search for entities which have left the colliding area.
		lastCollisions.stream()
				.filter(x -> !newCollisions.contains(x))
				.forEach(id -> {
					// Trigger the onExit method for all entities not inside the
					// script zone anymore.
						final Entity exitEntity = world.getEntity(id);
						if (exitEntity == null) {
							// Entity was deleted. Nevermind its gone.
							return;
						}

						final BestiaManager bm = bestiaMapper.get(exitEntity).bestiaManager;

						onExitBinding.put("target", bm);

						final MapScript mapScript = scriptFactory.getScript(script.script, bm);

						final boolean success = scriptManager.execute(mapScript, onExitBinding);
						checkSuccess(e.getId(), success);
					});

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
