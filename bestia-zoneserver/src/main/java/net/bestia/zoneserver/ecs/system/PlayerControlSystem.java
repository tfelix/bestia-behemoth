package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.game.zone.Zone;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

@Wire
public class PlayerControlSystem extends EntityProcessingSystem {
	
	private final Logger log = LogManager.getLogger(PlayerControlSystem.class);
	
	@Wire
	private Zone zone;
	
	ComponentMapper<PlayerControlled> pcm;

	@SuppressWarnings("unchecked")
	public PlayerControlSystem() {
		super(Aspect.getAspectForAll(PlayerControlled.class));
	}

	@Override
	protected void process(Entity player) {
		PlayerControlled playerControlled = pcm.get(player);
		
		log.debug("geht");
	}

}
