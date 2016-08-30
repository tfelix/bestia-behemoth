package net.bestia.zoneserver.generator.map;

public interface MapGenerator {

	/**
	 * Generates a new map and puts it into the map cache.
	 * 
	 * @param service
	 */
	void generate();

}
