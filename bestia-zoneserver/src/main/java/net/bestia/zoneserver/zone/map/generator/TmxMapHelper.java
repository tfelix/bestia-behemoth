package net.bestia.zoneserver.zone.map.generator;

import java.io.File;
import java.util.Vector;

import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.core.ObjectGroup;

/**
 * Helper class to give the sub classes useful methods to work on tiled maps.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TmxMapHelper {

	public TmxMapHelper() {
		super();
	}

	protected ObjectGroup getObjectLayer(Map tiledMap, String name) {
		final Vector<MapLayer> layers = tiledMap.getLayers();
		for (MapLayer layer : layers) {
			if (!(layer instanceof ObjectGroup)) {
				continue;
			}

			final ObjectGroup objLayer = (ObjectGroup) layer;

			if (!objLayer.getName().equalsIgnoreCase(name)) {
				continue;
			}

			return objLayer;
		}

		return null;
	}

	/**
	 * Returns the basic file name of the tiled map (without path).
	 * 
	 * @param tiledMap
	 * @return Basic filename of the map.
	 */
	protected String getMapName(Map tiledMap) {
		return new File(tiledMap.getFilename()).getName();
	}

}