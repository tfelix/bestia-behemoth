package net.bestia.zoneserver.game.zone.map;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import net.bestia.zoneserver.game.zone.map.Map.MapBuilder;

public class TMXMaploaderTest {

	@Test
	public void loadMapTest() throws Exception {
			
		URL resourceUrl = getClass().getResource("/data/maps/test1.tmx");
		Path resourcePath = Paths.get(resourceUrl.toURI());
		
		File mapFile = resourcePath.toFile();
		
		TMXMaploader loader = new TMXMaploader(mapFile);
		
		Map.MapBuilder builder = new MapBuilder();
		
		loader.loadMap(builder);
		
		
	}

}
