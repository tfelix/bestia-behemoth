package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedIteratingSystem;

import net.bestia.zoneserver.ecs.component.ScriptEntityTicker;

/**
 * Experimentelles System. Testen der Groovy Closures.
 * 
 * @author Thomas
 *
 */
@Wire
public class ScriptTickerSystem extends DelayedIteratingSystem {

	private ComponentMapper<ScriptEntityTicker> callMapper;

	public ScriptTickerSystem() {
		super(Aspect.all(ScriptEntityTicker.class));
	}

	@Override
	protected float getRemainingDelay(int entityId) {
		final ScriptEntityTicker set = callMapper.get(entityId);
		return set.cooldown;
	}

	@Override
	protected void processDelta(int entityId, float accumulatedDelta) {
		final ScriptEntityTicker set = callMapper.get(entityId);
		set.cooldown -= accumulatedDelta;
	}

	@Override
	protected void processExpired(int entityId) {
		final ScriptEntityTicker set = callMapper.get(entityId);
		
		set.fn.call();
		
		set.cooldown = set.interval;
		offerDelay(set.cooldown);
	}

}
