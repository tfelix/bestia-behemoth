package net.bestia.zoneserver.zone.map.generator;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.zone.Size;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.util.PackageLoader;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.map.Tileset;
import tiled.core.MapLayer;
import tiled.core.TileLayer;
import tiled.core.TileSet;
import tiled.io.TMXMapReader;

public class TmxMapGenerator implements MapGenerator {

	private final static Logger LOG = LoggerFactory.getLogger(TmxMapGenerator.class);

	private final MapService mapService;
	private final TMXMapReader reader;
	private String mapFile;

	private tiled.core.Map tiledMap;
	private Map bestiaMap;

	public TmxMapGenerator(MapService mapService, String tmxMapFile) {
		this.reader = new TMXMapReader();
		this.mapFile = new File(tmxMapFile).getAbsolutePath();
		this.mapService = Objects.requireNonNull(mapService);
	}

	@Override
	public void generate() {

		LOG.debug("Loading mapfile: {}", mapFile);

		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			LOG.error("Could not load mapfile.", e);
			return;
		}

		// Prepare basic data.
		final Size mapSize = new Size(tiledMap.getHeight(), tiledMap.getWidth());
		final String baseName = FilenameUtils.getBaseName(mapFile);
		final String mapDbName = FilenameUtils.removeExtension(baseName);

		bestiaMap = new Map(mapDbName, mapSize);

		// Read the tiles into the map builder.
		readTilesets();
		readTiles();

	}
	
	private void readTilesets() {
		final Vector<TileSet> tilesets = tiledMap.getTileSets();
		for(TileSet ts : tilesets) {
			int firstGid = ts.getFirstTile().getId();
			String name = ts.getName();
			
			Size size = new Size(320, 320);			
			
			Tileset bestiaTs = new Tileset(name, size, firstGid);
			
			mapService.saveTileset(bestiaTs);
		}
	}

	private void readTiles() {
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
			final String layerName = layer.getName().toLowerCase();
			if (!layerName.matches("layer_\\d+")) {
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

				// final Tile mapTile = new Tile(isWalkable, walkspeed);
				// builder.tiles.put(new Point(x, y), mapTile);
			}
		}
	}

}
