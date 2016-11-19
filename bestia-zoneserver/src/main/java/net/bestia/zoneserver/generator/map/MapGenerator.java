package net.bestia.zoneserver.generator.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapGenerator {

	private final static Logger LOG = LoggerFactory.getLogger(MapGenerator.class);

	/**
	 * Generates a new map and puts it into the map cache.
	 * 
	 * @param params
	 *            The basic parameter to perform the world creation with.
	 */
	void generate(MapBaseParameter params) {
		LOG.info("Generating world with: %s", params.toString());
		
		float[][] heightmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] tempmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] rainmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] magicmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] populationmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];

		

	}

}
