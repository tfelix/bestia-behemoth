package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.AI;

import com.artemis.Aspect;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

/**
 * The AI system will manage the current state of an entity, be responsible for
 * evaluating the environment and if necessary change the current state. The
 * state itself is responsible for doing "actions" (walking, attacking etc).
 * This state must be highly flexible.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class AISystem extends EntityProcessingSystem {

	@SuppressWarnings("unchecked")
	public AISystem() {
		super(Aspect.all(AI.class));
		// No op.
	}

	@Override
	protected void process(Entity e) {
		// TODO Auto-generated method stub

	}

}
