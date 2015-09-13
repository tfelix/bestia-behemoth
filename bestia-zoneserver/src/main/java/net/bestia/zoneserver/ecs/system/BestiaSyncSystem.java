package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.NPCBestiaManager;

import com.artemis.Aspect;
import com.artemis.Aspect.Builder;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

/**
 * The BestiaSyncSystem will take {@link NPCBestiaManager} components and sync them with the other components which are kinda "redundand" 
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class BestiaSyncSystem extends EntityProcessingSystem {

	private ComponentMapper<Position> posMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	
	public BestiaSyncSystem(Builder aspect) {
		super(Aspect.all(Bestia.class, Position.class));
		// no op.
	}

	@Override
	protected void process(Entity e) {
		
		final Position pos = posMapper.get(e);
		final Bestia bestia = bestiaMapper.get(e);
		
		final BestiaManager manager = bestia.bestiaManager;
		
		// Sync position.
		pos.x = manager.getLocation().getX();
		pos.y = manager.getLocation().getY();
	}

}
