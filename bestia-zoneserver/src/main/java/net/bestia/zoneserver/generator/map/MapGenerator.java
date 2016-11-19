package net.bestia.zoneserver.generator.map;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.shape.Size;

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
		
		final ThreadLocalRandom rand = ThreadLocalRandom.current();
		final Size worldSize = params.getWorldSize();
		
		float[][] heightmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] tempmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] rainmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] magicmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];
		float[][] populationmap = new float[(int)params.getWorldSize().getHeight() ][(int)params.getWorldSize().getWidth()];

		// Generate random data for the maps via simplex noise.
		SimplexNoise[] noise = new SimplexNoise[5];
		for(int i = 0; i < 5; i++) {
			noise[i] = new SimplexNoise(rand.nextInt());
		}
		
		for(int y = 0; y < worldSize.getHeight(); y++) {
			for(int x = 0; x < worldSize.getWidth(); x++) {
				heightmap[y][x] = (float) noise[0].simplex2(x, y);
				tempmap[y][x] = (float) noise[1].simplex2(x, y);
				rainmap[y][x] = (float) noise[2].simplex2(x, y);
				magicmap[y][x] = (float) noise[3].simplex2(x, y);
				populationmap[y][x] = (float) noise[4].simplex2(x, y);
			}
		}
		
		
	}

}
