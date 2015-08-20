package net.bestia.zoneserver.zone.map;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import net.bestia.zoneserver.zone.Vector2;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tiled.core.MapLayer;
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

		final Properties mapProperties = tiledMap.getProperties();

		// Get map properties.
		builder.globalScript = mapProperties.getProperty("globalScript");
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

			// Ignore non bottom/ground layers. (Like special sound layer, event trigger etc.)
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
					//final String pWalkspeed = p.getProperty("walkspeed");

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
