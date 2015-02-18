package net.bestia.core.game.zone;

import java.util.ArrayList;
import java.util.List;

public class PropertyLayer {
	
	private final List<Property> properties;
	private final String name;
	private final int sizeX;
	private final int sizeY;

	public PropertyLayer(String propertyName, int sizeX, int sizeY) {
		
		this.name = propertyName;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		
		final long count = sizeX * sizeY;
		properties = new ArrayList<Property>(sizeX * sizeY);
		
		for(long i = 0; i < count; i++) {
			properties.add(new Property());
		}
	}
	
	public int getValue(int x, int y) {
		return properties.get(sizeX * y + x).getValue();
	}
	
	public void updateTick() {
		// Calculate the average.
	}
	
	private int calculateAverage() {
		int average = 0;
		for (int y = 0; y < sizeX; y++) {
			int xAverage = 0;
			for(int x = 0; x < sizeY; x++) {
				xAverage += properties.get(sizeX * y + x).getValue();
			}
			xAverage /= sizeX;
			average += xAverage;
		}		
		return average / sizeY;
	}
}
