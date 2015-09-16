package net.bestia.zoneserver.zone.map;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.map.TMXMaploader;

public class TMXMaploaderTest {

	@Test
	public void loadMapTest() throws Exception {
			
		URL resourceUrl = getClass().getResource("/data/maps/test-zone1.tmx");
		Path resourcePath = Paths.get(resourceUrl.toURI());
		
		File mapFile = resourcePath.toFile();
		
		TMXMaploader loader = new TMXMaploader(mapFile);
		
		Map.MapBuilder builder = new MapBuilder();
		
		loader.loadMap(builder);
	}

}
