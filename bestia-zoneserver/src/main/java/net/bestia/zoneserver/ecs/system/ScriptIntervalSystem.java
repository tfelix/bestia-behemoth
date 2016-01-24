package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.Aspect.Builder;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedIteratingSystem;

import net.bestia.zoneserver.ecs.component.Delay;
import net.bestia.zoneserver.ecs.component.ScriptCallable;

/**
 * Triggers script functions on a basic interval.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ScriptIntervalSystem extends DelayedIteratingSystem {
	
	private static final Logger LOG = LogManager.getLogger(ScriptIntervalSystem.class);
	
	private ComponentMapper<ScriptCallable> callableMapper;
	private ComponentMapper<Delay> delayMapper;

	public ScriptIntervalSystem(Builder aspect) {
		super(Aspect.all(Delay.class, ScriptCallable.class));
		
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
	protected void processExpired(int entityId) {
		final ScriptCallable sc = callableMapper.get(entityId);
		try {
			sc.fn.call();
		} catch (Exception e) {
			LOG.error("Could not execute interval based script. Removing script.", e);
			// Deleting entity.
			world.delete(entityId);
		}
		
		// Setup new delay.
		final Delay d = delayMapper.get(entityId);
		d.setTimer(d.getDelay());
		offerDelay(d.getTimer());
	}

}
