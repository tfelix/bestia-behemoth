package net.bestia.zoneserver.generator.map;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.map.Tile;
import net.bestia.model.map.TileProperties;
import net.bestia.model.map.Tileset;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Size;
import net.bestia.zoneserver.service.MapService;
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

	public TmxMapGenerator(MapService mapService, String tmxMapFile) {
		// Read also tile maps.
		this.reader = new TMXMapReader(true);
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
		
		// Read the tiles into the map builder.
		readTilesets();
		readTiles();
	}

	private void readTilesets() {
		final Vector<TileSet> tilesets = tiledMap.getTileSets();
		for (TileSet ts : tilesets) {
			int firstGid = ts.getFirstTile().getId();
			String name = ts.getName();
			
			// TODO This is hard coded just because.
			int width = 320;
			int height = 320;
			
			Tileset bestiaTs = new Tileset(name, new Size(width, height), firstGid);

			mapService.saveTileset(bestiaTs);
			
			readTileproperties(ts);
		}
	}

	/**
	 * Reads the important properties of the tiles in a tileset and saves them
	 * into the memory db.
	 * 
	 * @param ts
	 */
	private void readTileproperties(TileSet ts) {
		for(int i = ts.getFirstTile().getId(); i < ts.getMaxTileId(); i++) {
			final tiled.core.Tile t = ts.getTile(i);
			final Properties props = t.getProperties();
			
			boolean isWalkable = true;
			int walkspeed = 100;
			if(props.getProperty("isWalkable") != null) {
				isWalkable = Boolean.parseBoolean(props.getProperty("isWalkable"));
			}
			if(props.getProperty("walkspeed") != null) {
				walkspeed = Integer.parseInt(props.getProperty("walkspeed"));
			}
			
			final TileProperties tileProps = new TileProperties(isWalkable, walkspeed);
			mapService.saveTileProperties(i, tileProps);
		}
	}

	/**
	 * Read the tiles and save it into the memory database.
	 */
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
		final List<Tile> bestiaTiles = new ArrayList<>();
		for (int i = 0; i < layers.size(); i++) {
			
			bestiaTiles.clear();
			
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {

					final TileLayer layer = layers.get(i);
					final tiled.core.Tile tile = layer.getTileAt(x, y);

					if (tile == null) {
						continue;
					}

					//bestiaTiles.add(new Tile(i, new Point(x, y), tile.getId()));
				}
			}

			mapService.saveTiles(i, bestiaTiles);
		}
	}

}
