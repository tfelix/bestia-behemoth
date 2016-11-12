package net.bestia.zoneserver.generator.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BiomeSelector {
	
	private final static List<BiomeParameters> parameters = new ArrayList<>();
	private final static Map<Integer, BiomeParameters> lookup = new HashMap<>();
	
	static {
		parameters.add(new BiomeParameters("Desert", 0.01f, 0f, 0.5f, 8));
		parameters.add(new BiomeParameters("Beach", 0.01f, 0f, 0.2f, 8));
		parameters.add(new BiomeParameters("Forrest", 0.01f, 0f, 0.5f, 8));
	}
	
	public BiomeParameters select(float height, float temp, float rainfall) {
		
		return null;
	}
	
	public float biomeDistance(float height, float temp, float rainfall, BiomeParameters params) {
		return 0f;
	}

}
