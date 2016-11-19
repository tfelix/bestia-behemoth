package net.bestia.zoneserver.generator.map;

/**
 * This class is used to define after which of the values a single biome is
 * choosen.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class BiomeSelector {

	private final String name;

	private float height;
	private float rainfall;
	private float temperature;

	private Biome biome;

	public BiomeSelector(String name, float height, float rainfall, float temperature, Biome biome) {

		this.name = name;
		this.height = height;
		this.rainfall = rainfall;
		this.temperature = temperature;
		this.biome = biome;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	public float getRainfall() {
		return rainfall;
	}

	public void setRainfall(float rainfall) {
		this.rainfall = rainfall;
	}

	public float getTemperature() {
		return temperature;
	}

	public void setTemperature(float temperature) {
		this.temperature = temperature;
	}

	public Biome getBiome() {
		return biome;
	}

	public String getName() {
		return name;
	}
}
