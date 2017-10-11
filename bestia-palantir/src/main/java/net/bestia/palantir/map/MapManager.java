package net.bestia.palantir.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This manager holds all the business logic to load and render a bestia map. In
 * order to do so it needs first a map config file and then it needs to load the
 * associated map data. It also needs a reference to the tile map data before it
 * can start to render the map.
 * 
 * @author Thomas Felix
 *
 */
public class MapManager {
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private MapConfig mapConfig;
	
	public MapManager() {
		
	}
	
	public void loadMapConfig(File config) throws IOException {
		mapConfig = mapper.readValue(config, MapConfig.class);
	}
	
	public void setTilemapFolder(Path tilemapFolder) {
		
	}
	
	public void setMapData(Path dataFolder) {
		
	}

}
