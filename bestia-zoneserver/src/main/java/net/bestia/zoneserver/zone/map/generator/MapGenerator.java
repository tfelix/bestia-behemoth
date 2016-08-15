package net.bestia.zoneserver.zone.map.generator;

public interface MapGenerator {

	/**
	 * Generates a new map and puts it into the map cache.
	 * 
	 * @param service
	 */
	void generate();

}
