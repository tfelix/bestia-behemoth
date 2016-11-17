package net.bestia.zoneserver;

import org.junit.Test;

import net.bestia.model.shape.Size;
import net.bestia.zoneserver.generator.map.SimplexNoise;

public class MapCreatorTest {

	@Test
	public void createMap() {

		Size mapSize = new Size(1000, 1000);

		float[][] temprature = new float[(int) mapSize.getHeight()][(int) mapSize.getWidth()];
		float[][] rainfall = new float[(int) mapSize.getHeight()][(int) mapSize.getWidth()];
		float[][] height = new float[(int) mapSize.getHeight()][(int) mapSize.getWidth()];
		
		SimplexNoise noiseGen1 = new SimplexNoise();
		SimplexNoise noiseGen2 = new SimplexNoise();
		SimplexNoise noiseGen3 = new SimplexNoise();
		noiseGen1.seed(1234);
		noiseGen2.seed(1234);
		noiseGen3.seed(1234);
		
		for(int y = 0; y < mapSize.getHeight(); y++) {
			for(int x = 0; x < mapSize.getWidth(); x++) {
				
				temprature[y][x] = (float) noiseGen1.simplex2(x, y);
				rainfall[y][x] = (float) noiseGen2.simplex2(x, y);
				height[y][x] = (float) noiseGen3.simplex2(x, y);
				
			}
		}

		System.out.println("Finished");
	}

}
