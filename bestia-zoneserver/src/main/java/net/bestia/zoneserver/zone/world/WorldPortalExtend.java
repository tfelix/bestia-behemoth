package net.bestia.zoneserver.zone.world;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.ecs.component.Collision;
import net.bestia.zoneserver.ecs.component.TriggerScript;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Map.Script;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;

/**
 * Extends the ECS system with world map portals.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class WorldPortalExtend implements WorldExtend {
	
	private static final Logger log = LogManager.getLogger(WorldPortalExtend.class);

	@Override
	public void extend(World world, Map map) {
		log.trace("Generating map extras: Portals...");
		
		// Get all the portals and build portal script entities.
		final List<Script> portals = map.getPortals();
		
		// Add entities for each map portal to the ECS.
		for(Script portal : portals) {
			final Entity e = world.createEntity();
			final EntityEdit ee = e.edit();
			
			final Collision collision = ee.create(Collision.class);
			collision.shape = portal.getShape();
			final TriggerScript trigger = ee.create(TriggerScript.class);
			trigger.script = portal.getMapScript();
		}
		
		log.trace("Added {} portal(s).", portals.size());
	}

}
