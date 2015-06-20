package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.PlayerControlled;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

@Wire
public class PlayerControlSystem extends EntityProcessingSystem {
	
	ComponentMapper<PlayerControlled> pcm;

	@SuppressWarnings("unchecked")
	public PlayerControlSystem(Aspect aspect) {
		super(Aspect.getAspectForAll(PlayerControlled.class));
	}

	@Override
	protected void process(Entity player) {
		PlayerControlled playerControlled = pcm.get(player);
		
		
	}

}
