package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.DelayedRemove;

/**
 * After the delay on the entity component has expired, the entity will be
 * deleted. Please register this system as the last one since entities with
 * delay of 0 ms should be executed ONCE before they are removed from the ECS.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class DelayedRemoveSystem extends DelayedEntityProcessingSystem {

	private ComponentMapper<DelayedRemove> removeMapper;
	
	public DelayedRemoveSystem() {
		super(Aspect.all(DelayedRemove.class));
		// no op.
	}

	@Override
	protected float getRemainingDelay(Entity e) {
		return removeMapper.get(e).removeDelay;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		DelayedRemove removeComp = removeMapper.get(e);
		removeComp.removeDelay -= accumulatedDelta;
	}

	@Override
	protected void processExpired(Entity e) {
		e.deleteFromWorld();
	}

}
