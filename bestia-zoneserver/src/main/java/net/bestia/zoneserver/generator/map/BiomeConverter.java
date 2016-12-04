package net.bestia.zoneserver.generator.map;

import java.util.ArrayList;
import java.util.List;

/**
 * The biome converter does a fuzzy logic comparison of the different biomes
 * with the parameter which are used to determine a certain biome.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BiomeConverter {

	private final static List<BiomeSelector> parameters = new ArrayList<>();

	static {
		parameters.add(new BiomeSelector("Desert", 0.01f, 0f, 0.5f, Biome.DESERT));
		parameters.add(new BiomeSelector("Beach", 0.01f, 0f, 0.2f, Biome.BEACH));
		parameters.add(new BiomeSelector("Forrest", 0.01f, 0f, 0.5f, Biome.FORREST));
	}

	/**
	 * Selects a given biome for the parameter given.
	 * 
	 * @param height
	 *            Heigh.
	 * @param temp
	 *            Average temperature.
	 * @param rainfall
	 *            Amount of rainfall.
	 * @return The closes matching biome for this parameters.
	 */
	public Biome select(float height, float temp, float rainfall) {

		// Default.
		Biome bestBiome = Biome.FORREST;
		float bestMatch = Float.MAX_VALUE;

		for (BiomeSelector para : parameters) {

			float dTotal = Math.abs(height - para.getHeight());
			dTotal += Math.abs(temp - para.getTemperature());
			dTotal += Math.abs(rainfall) - para.getRainfall();

			if (dTotal < bestMatch) {
				bestMatch = dTotal;
				bestBiome = para.getBiome();
			}
		}

		return bestBiome;
	}

}
