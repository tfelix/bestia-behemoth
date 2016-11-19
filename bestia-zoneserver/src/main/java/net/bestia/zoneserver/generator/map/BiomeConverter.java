package net.bestia.zoneserver.generator.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BiomeConverter {
	
	private final static List<BiomeSelector> parameters = new ArrayList<>();
	private final static Map<Integer, BiomeSelector> lookup = new HashMap<>();
	
	static {
		parameters.add(new BiomeSelector("Desert", 0.01f, 0f, 0.5f, Biome.DESERT));
		parameters.add(new BiomeSelector("Beach", 0.01f, 0f, 0.2f, Biome.BEACH));
		parameters.add(new BiomeSelector("Forrest", 0.01f, 0f, 0.5f, Biome.FORREST));
	}
	
	public BiomeSelector select(float height, float temp, float rainfall) {
		
		return null;
	}
	
	public float biomeDistance(float height, float temp, float rainfall, BiomeSelector params) {
		return 0f;
	}

}
