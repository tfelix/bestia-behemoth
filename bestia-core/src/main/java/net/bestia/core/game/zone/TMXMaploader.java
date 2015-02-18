package net.bestia.core.game.zone;

import java.io.File;
import java.io.IOException;

import tiled.io.TMXMapReader;

public class TMXMaploader implements Maploader {
	
	private TMXMapReader reader;
	private String mapFile;

	public TMXMaploader(File tmxMapFile) {
		
		this.mapFile = tmxMapFile.getAbsolutePath();
	}
	
	public void loadMap(Map.Mapbuilder builder) throws IOException {
		tiled.core.Map tiledMap;
		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			throw new IOException(e);
		}
		
		builder.height = tiledMap.getHeight();
		builder.width = tiledMap.getWidth();
		
		
	}
}
