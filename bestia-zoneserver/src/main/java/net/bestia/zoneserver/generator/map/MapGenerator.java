package net.bestia.zoneserver.generator.map;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.geometry.Size;

public class MapGenerator {

	private final static Logger LOG = LoggerFactory.getLogger(MapGenerator.class);
	private final BiomeConverter converter = new BiomeConverter();

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

		final int height = (int) params.getWorldSize().getHeight();
		final int width = (int) params.getWorldSize().getWidth();

		float[][] heightmap = new float[height][width];
		float[][] tempmap = new float[height][width];
		float[][] rainmap = new float[height][width];
		float[][] magicmap = new float[height][width];
		float[][] populationmap = new float[height][width];

		// Generate random data for the maps via simplex noise.
		OpenSimplexNoise[] noise = new OpenSimplexNoise[5];
		for (int i = 0; i < 5; i++) {
			noise[i] = new OpenSimplexNoise(rand.nextInt());
		}

		for (int y = 0; y < worldSize.getHeight(); y++) {
			for (int x = 0; x < worldSize.getWidth(); x++) {
				heightmap[y][x] = (float) noise[0].eval(x, y);
				tempmap[y][x] = (float) noise[1].eval(x, y);
				rainmap[y][x] = (float) noise[2].eval(x, y);
				magicmap[y][x] = (float) noise[3].eval(x, y);
				populationmap[y][x] = (float) noise[4].eval(x, y);
			}
		}

		// Max height of 8km.
		calculateHeightmap(heightmap, 8000);

		Biome[][] biomes = calculateBiomeMap(worldSize, heightmap, tempmap, rainmap);
	}

	/**
	 * The noise map (which should be between -1 and 1 is now transformed into a
	 * height map with the scale of meters).
	 * 
	 * @param map
	 * @param minHeight
	 * @param maxHeight
	 */
	private void calculateHeightmap(float[][] map, int maxHeight) {
		for (int y = 0; y < map.length; y++) {
			for (int x = 0; x < map[0].length; x++) {
				map[y][x] += 1.0f;
				// Normalize and multiply.
				map[y][x] = (map[y][x] / 2.0f) * maxHeight;
			}
		}
	}

	private Biome[][] calculateBiomeMap(Size size, float[][] heightmap, float[][] tempmap, float[][] rainmap) {

		final Biome[][] biomes = new Biome[(int) size.getHeight()][(int) size.getWidth()];

		for (int y = 0; y < size.getHeight(); y++) {
			for (int x = 0; x < size.getWidth(); x++) {

				final float height = heightmap[y][x];
				final float temp = tempmap[y][x];
				final float rainfall = rainmap[y][x];

				biomes[y][x] = converter.select(height, temp, rainfall);
			}
		}
		
		return biomes;
	}

	/**
	 * This will create a histogram of the given map divided evenly into the
	 * buckets.
	 * 
	 * @param map
	 * @param buckets
	 * @return
	 */
	private float[] histogram(float[][] map, int buckets) {

		float maxV = -1000000f;
		float minV = 10000000;
		for (int y = 0; y < map.length; y++) {
			for (int x = 0; x < map[0].length; x++) {
				if (map[y][x] > maxV) {
					maxV = map[y][x];
				}
				if (map[y][x] < minV) {
					minV = map[y][x];
				}
			}
		}

		float d = maxV - minV;
		float bucketSize = d / buckets;
		long size = map.length * map[0].length;

		float[] histogram = new float[buckets];

		for (int y = 0; y < map.length; y++) {
			for (int x = 0; x < map[0].length; x++) {
				int bucket = (int) ((minV - map[y][x]) / bucketSize);
				histogram[bucket] += 1f / size;
			}
		}

		return histogram;
	}

}
