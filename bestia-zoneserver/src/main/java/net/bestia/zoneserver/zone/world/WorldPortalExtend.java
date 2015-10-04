package net.bestia.zoneserver.zone.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.map.Map;

import com.artemis.World;

class WorldPortalExtend implements WorldExtend {
	
	private static final Logger log = LogManager.getLogger(WorldPortalExtend.class);

	@Override
	public void extend(World world, Map map) {
		log.trace("Generating map extras: Portals...");
		
		// Get all the portals and build portal script entities.
	}

}
