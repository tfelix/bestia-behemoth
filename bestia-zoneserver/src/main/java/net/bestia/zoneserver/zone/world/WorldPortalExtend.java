package net.bestia.zoneserver.zone.world;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.TriggerScript;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Map.Script;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;
import com.artemis.WorldConfiguration;

/**
 * Extends the ECS system with world map portals.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class WorldPortalExtend implements WorldExtend {
	
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
			
			final Position position = ee.create(Position.class);
			position.position = portal.getShape();
			final TriggerScript trigger = ee.create(TriggerScript.class);
			trigger.script = portal.getMapScript();
		}
		
		log.trace("Added {} portal(s).", portals.size());
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx) {
		// no op.
	}

}
