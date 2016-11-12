package net.bestia.zoneserver.generator.map;

public class BiomeParameters {

	private final String name;

	private float height;
	private float rainfall;
	private float temperature;

	private int mapIndex;

	public BiomeParameters(String name, float height, float rainfall, float temperature, int index) {
		
		this.name = name;
		this.height = height;
		this.rainfall = rainfall;
		this.temperature = temperature;
		this.mapIndex = index;
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

	public int getMapIndex() {
		return mapIndex;
	}

	public void setMapIndex(int mapIndex) {
		this.mapIndex = mapIndex;
	}

	public String getName() {
		return name;
	}

}
