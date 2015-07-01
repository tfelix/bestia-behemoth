package net.bestia.zoneserver.game.zone.map;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import net.bestia.zoneserver.game.zone.Vector2;

import org.apache.commons.io.FilenameUtils;

import tiled.core.MapLayer;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;
import tiled.core.TileLayer;
import tiled.core.TileSet;
import tiled.io.TMXMapReader;

/**
 * Loads a TMX map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TMXMaploader implements Maploader {

	private TMXMapReader reader;
	private String mapFile;

	/**
	 * 
	 * @param tmxMapFile
	 */
	public TMXMaploader(File tmxMapFile) {
		this.reader = new TMXMapReader();
		this.mapFile = tmxMapFile.getAbsolutePath();
	}

	public void loadMap(Map.MapBuilder builder) throws IOException {
		tiled.core.Map tiledMap;
		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			throw new IOException(e);
		}

		builder.height = tiledMap.getHeight();
		builder.width = tiledMap.getWidth();

		String filename = FilenameUtils.removeExtension(FilenameUtils.getBaseName(mapFile));
		builder.mapDbName = filename;

		Properties p = tiledMap.getProperties();

		// Get map properties.
		builder.globalScript = p.getProperty("globalScript");

		checkStaticCollisions(tiledMap);

		for (MapLayer l : tiledMap.getLayers()) {
			String name = l.getName().toUpperCase();
			if (name.equals("WALLS")) {
				setWalls(l);
			} else if (name.equals("SCRIPTS")) {
				setScripts(l);
			} else if (name.equals("SOUNDS")) {
				setSounds(l);
			}
		}
	}

	private void setSounds(MapLayer l) {
		// TODO Auto-generated method stub

	}

	private Set<Vector2> checkStaticCollisions(tiled.core.Map tiledMap) {
		Set<Vector2> collisions = new HashSet<>();

		int numLayer = tiledMap.getLayerCount();
		Vector<TileSet> tileSet = tiledMap.getTileSets();
		
		// Find the ground tileset with MUST contain all collidable tiles.
		final TileSet ts;
		for (TileSet curTs : tileSet) {
			if(curTs.getName().equals("Berge")) {
				ts = curTs;
				break;
			}
		}

		for (int i = 0; i < numLayer; i++) {
			MapLayer layer = tiledMap.getLayer(i);
			TileLayer tLayer;
			if(layer instanceof TileLayer) {
				tLayer = (TileLayer) layer;
			} else {
				continue;
			}
			
			final int height = layer.getHeight();
			final int width = layer.getWidth();
			
			// Ignore non bottom/ground layer.
			if (!layer.getName().toLowerCase().startsWith("layer_")) {
				continue;
			}

			for(int y = 0; y < height; y++) {
				for(int x = 0; x < width; x++) {					
					Properties p = tLayer.getTileInstancePropertiesAt(22, 13);
					tiled.core.Map map = layer.getMap();
					
					//ts.getTile(width)
				}			
			}
		}

		return null;
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
