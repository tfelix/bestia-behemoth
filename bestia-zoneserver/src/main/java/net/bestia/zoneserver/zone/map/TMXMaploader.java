package net.bestia.zoneserver.zone.map;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.shape.Vector2;
import tiled.core.MapLayer;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;
import tiled.core.TileLayer;
import tiled.io.TMXMapReader;

/**
 * Loads a TMX map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TMXMaploader implements Maploader {

	private final static Logger log = LogManager.getLogger(TMXMaploader.class);

	private final TMXMapReader reader;
	private final String mapFile;
	private Map.MapBuilder builder;

	/**
	 * 
	 * @param tmxMapFile
	 */
	public TMXMaploader(File tmxMapFile) {
		this.reader = new TMXMapReader();
		this.mapFile = tmxMapFile.getAbsolutePath();
	}

	public void loadMap(Map.MapBuilder builder) throws IOException {

		log.debug("Loading mapfile: {}", mapFile);

		this.builder = builder;
		tiled.core.Map tiledMap;
		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			throw new IOException(e);
		}

		prepareMapData(tiledMap);

		prepareTilesData(tiledMap);

		prepareGlobalMapscripts(tiledMap);
		
		prepareTriggerMapscripts(tiledMap);
	}

	private void prepareTriggerMapscripts(tiled.core.Map tiledMap) {
		Vector<MapLayer> layers = tiledMap.getLayers();
		for(MapLayer layer : layers) {
			if(!(layer instanceof ObjectGroup)) {
				continue;
			}
			
			final ObjectGroup objLayer = (ObjectGroup) layer;
			
			// Basically we are looking for two layers: the portal layer and the "normal" script layer.
			final String layerName = objLayer.getName().toLowerCase();
			if(layerName.equals("portals"))  {
				
				// Create the portal scripts.
				final Iterator<MapObject> objIter = objLayer.getObjects();
				while(objIter.hasNext()) {
					
					//final MapObject mapObj = objIter.next();
					//mapObj.getBounds().
				}
				
			} else if(layerName.equals("scripts")) {
				
			} else {
				// Unknown script layer. Ignore.
				log.warn("Found unknown script layer: {}", objLayer.getName());
			}
		}
	}

	/**
	 * Reads the map scripts, parses them into the right format and fills the
	 * builder with it.
	 * 
	 * @param tiledMap
	 */
	private void prepareGlobalMapscripts(tiled.core.Map tiledMap) {
		final String scriptStr = tiledMap.getProperties().getProperty(
				"globalScripts");

		if (scriptStr == null) {
			builder.mapscripts = new ArrayList<>();
			return;
		}

		final List<String> scripts = Arrays.stream(scriptStr.split(","))
				.map((String x) -> x.trim()).collect(Collectors.toList());
		
		builder.globalMapscripts = scripts;
	}

	/**
	 * Translates general map data.
	 * 
	 * @param tiledMap
	 */
	private void prepareMapData(tiled.core.Map tiledMap) {
		builder.height = tiledMap.getHeight();
		builder.width = tiledMap.getWidth();

		final String baseName = FilenameUtils.getBaseName(mapFile);
		final String mapDbName = FilenameUtils.removeExtension(baseName);
		builder.mapDbName = mapDbName;

		//final Properties mapProperties = tiledMap.getProperties();
	}

	/**
	 * Translates the map tile data into bestia usable tile informations.
	 * 
	 * @param tiledMap
	 */
	private void prepareTilesData(tiled.core.Map tiledMap) throws IOException {

		int numLayer = tiledMap.getLayerCount();

		// We must sort the layer since order is not guranteed.
		List<TileLayer> layers = new ArrayList<>();

		// Extract all ground layers.
		for (int i = 0; i < numLayer; i++) {

			MapLayer layer = tiledMap.getLayer(i);

			TileLayer tLayer;
			if (layer instanceof TileLayer) {
				tLayer = (TileLayer) layer;
			} else {
				continue;
			}

			// Ignore non bottom/ground layers. (Like special sound layer, event
			// trigger etc.)
			if (!layer.getName().toLowerCase().startsWith("layer_")) {
				continue;
			}

			layers.add(tLayer);
		}

		// Sort the layer list. Ascending order.
		layers.sort(new Comparator<MapLayer>() {

			@Override
			public int compare(MapLayer o1, MapLayer o2) {
				// Snip "layer_" away.
				int o1i = Integer.parseInt(o1.getName().substring(6));
				int o2i = Integer.parseInt(o2.getName().substring(6));

				if (o1i == o2i) {
					return 0;
				}
				return (o1i < o2i) ? -1 : 1;
			}

		});

		// Sanity checks.
		if (layers.isEmpty()) {
			throw new IOException("No tiles to load have been found.");
		}

		// Save the size of the map.
		final int height = layers.get(0).getHeight();
		final int width = layers.get(0).getWidth();

		// Iterate bottom up through tiles and layers.
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {

				boolean isWalkable = true;
				int walkspeed = 1000;

				for (int i = 0; i < layers.size(); i++) {

					final TileLayer layer = layers.get(i);
					final tiled.core.Tile tile = layer.getTileAt(x, y);

					if (tile == null) {
						continue;
					}

					Properties p = tile.getProperties();

					if (p == null) {
						continue;
					}

					final String pWalkable = p.getProperty("bWalkable");
					// final String pWalkspeed = p.getProperty("walkspeed");

					if (pWalkable != null && pWalkable.equals("false")) {
						isWalkable = false;
					} else {
						isWalkable = true;
					}
				}

				final Tile mapTile = new Tile(isWalkable, walkspeed);
				builder.tiles.put(new Vector2(x, y), mapTile);
			}
		}
	}
}
