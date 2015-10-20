package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect.Builder;
import com.artemis.Aspect;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.MobSpawn;

/**
 * Converts spawn entities to real mob entities after the spawn delay.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MobSpawnSystem extends DelayedEntityProcessingSystem {

	public MobSpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}

	@Override
	protected float getRemainingDelay(Entity e) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void processExpired(Entity e) {
		// TODO Auto-generated method stub

	}

}
