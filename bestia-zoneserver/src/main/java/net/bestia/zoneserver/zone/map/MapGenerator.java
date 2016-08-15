package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.service.MapService;

public interface MapGenerator {

	/**
	 * Generates a new map and puts it into the map cache.
	 * 
	 * @param service
	 */
	void generate(MapService service);

}
