package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;

import com.artemis.Aspect;
import com.artemis.Entity;
import com.artemis.systems.EntityProcessingSystem;

// TODO Besser ein https://github.com/junkdog/artemis-odb/wiki/DelayedEntityProcessingSystem nutzen.
public class MovementSystem extends EntityProcessingSystem {

	@SuppressWarnings("unchecked")
	public MovementSystem() {
		super(Aspect.getAspectForAll(Position.class, Movement.class));
		
	}

	@Override
	protected void process(Entity entity) {
		// TODO Auto-generated method stub
		
	}

}
