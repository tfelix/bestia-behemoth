package net.bestia.core.game.zone.map;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

import tiled.core.MapLayer;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;
import tiled.core.TileSet;
import tiled.io.TMXMapReader;

public class TMXMaploader implements Maploader {

	private TMXMapReader reader;
	private String mapFile;

	public TMXMaploader(File tmxMapFile) {
		this.reader = new TMXMapReader();
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

		Properties p = tiledMap.getProperties();

		// Get map properties.
		builder.globalScript = p.getProperty("globalScript");

		for (TileSet ts : tiledMap.getTileSets()) {
			// ts.
		}

		for (MapLayer l : tiledMap.getLayers()) {
			String name = l.getName().toUpperCase();
			if (name.equals("WALLS")) {
				setWalls(l);
			} else if (name.equals("SCRIPTS")) {
				setScripts(l);
			} else if(name.equals("SOUNDS")) {
				setSounds(l);
			}
		}
	}

	private void setSounds(MapLayer l) {
		// TODO Auto-generated method stub
		
	}

	private void setScripts(MapLayer l) {
		ObjectGroup grp = (ObjectGroup) l;
		Iterator<MapObject> itObj = grp.getObjects();
		
		while (itObj.hasNext()) {
			MapObject obj = itObj.next();
			Rectangle rect = obj.getBounds();

		}
	}

	private void setWalls(MapLayer l) {
		ObjectGroup grp = (ObjectGroup) l;
		Iterator<MapObject> itObj = grp.getObjects();
		
		while (itObj.hasNext()) {
			MapObject obj = itObj.next();
			Rectangle rect = obj.getBounds();

		}
	}
}
