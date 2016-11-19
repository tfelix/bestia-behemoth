package net.bestia.zoneserver.generator.map;

import org.junit.Test;

import net.bestia.model.shape.Size;

public class MapGeneratorTest {

	private MapGenerator mapGen = new MapGenerator();

	@Test
	public void createMap() {

		final MapBaseParameter params = new MapBaseParameter(100, new Size(4000, 4000), 0.5f, 6, 300);
		mapGen.generate(params);
	}

}
