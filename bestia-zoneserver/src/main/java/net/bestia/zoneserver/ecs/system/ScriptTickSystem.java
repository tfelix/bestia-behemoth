package net.bestia.zoneserver.ecs.system;

import java.util.Set;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Delay;
import net.bestia.zoneserver.ecs.component.Script;
import net.bestia.zoneserver.proxy.BestiaEntityProxy;
import net.bestia.zoneserver.script.MapScript;
import net.bestia.zoneserver.script.MapScriptFactory;
import net.bestia.zoneserver.script.ScriptManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedIteratingSystem;

/**
 * This system manages the periodically invocation of map trigger scripts. It
 * will periodically check for colliding entities and will then trigger ther
 * script part.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ScriptTickSystem extends DelayedIteratingSystem {

	private static final Logger LOG = LogManager.getLogger(ScriptTickSystem.class);

	private ComponentMapper<Script> scriptMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Delay> delayMapper;

	@Wire
	private MapScriptFactory scriptFactory;

	/**
	 * Just needed to extract script manager as a shortcut.
	 */
	@Wire
	private CommandContext ctx;

	private ScriptManager scriptManager;

	private final Bindings onInsideBinding = new SimpleBindings();

	public ScriptTickSystem() {
		super(Aspect.all(Delay.class, Script.class));

		onInsideBinding.put("event", "onInside");
	}

	@Override
	protected void initialize() {
		super.initialize();

		scriptManager = ctx.getScriptManager();
	}

	@Override
	protected float getRemainingDelay(int entityId) {
		final Delay d = delayMapper.get(entityId);
		return d.getTimer();
	}

	@Override
	protected void processDelta(int entityId, float accumulatedDelta) {
		final Delay d = delayMapper.get(entityId);
		d.setTimer(d.getTimer() - accumulatedDelta);
	}

	@Override
	protected void processExpired(int scriptId) {

		final Script script = scriptMapper.get(scriptId);
		final Set<Integer> touchingEntities = script.lastTriggeredEntities;

		if (touchingEntities.size() > 0) {
			LOG.trace("Script {} touched by entities: {}", script.script, touchingEntities);
		}

		for (Integer id : touchingEntities) {
			final Entity e = world.getEntity(id);
			if (e == null) {
				// Entity seems deleted... just remove it.
				touchingEntities.remove(id);
				continue;
			}

			// We still touch the script.
			final BestiaEntityProxy bm = bestiaMapper.get(id).bestiaManager;

			onInsideBinding.put("target", bm);

			final MapScript mapScript = scriptFactory.getScript(script.script, bm);

			final boolean success = scriptManager.execute(mapScript, onInsideBinding);
			if (!success) {
				LOG.warn("Script {} was not executed. Was removed from the world.", script.script);
				world.delete(scriptId);
			}
		}

		// Setup new delay.
		final Delay d = delayMapper.get(scriptId);
		d.setTimer(d.getDelay());
		offerDelay(d.getTimer());
	}

}
