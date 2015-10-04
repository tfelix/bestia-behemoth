package net.bestia.zoneserver.zone.map.tmx;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Maploader;
import net.bestia.zoneserver.zone.map.Tile;
import net.bestia.zoneserver.zone.shape.Vector2;
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
	private String mapFile;
	private Map.MapBuilder builder;
	private final static Set<TMXMapExtender> extras = new HashSet<>();

	static {
		// TODO dieses Autoloading von Extendern in eigene Klasse auslagern per
		// Template. Siehe WorldExtender. Siehe Command Factory.
		final Reflections reflections = new Reflections("net.bestia.zoneserver.zone.map.tmx");
		final Set<Class<? extends TMXMapExtender>> subTypes = reflections.getSubTypesOf(TMXMapExtender.class);

		for (Class<? extends TMXMapExtender> clazz : subTypes) {

			// Dont instance abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			try {
				final TMXMapExtender extra = clazz.newInstance();
				extras.add(extra);
			} catch (InstantiationException | IllegalAccessException e) {
				log.error("Can not instanciate : {}", clazz.toString(), e);
			}
		}
	}

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
		
		// Extend the map with all missing features.
		extendMapBuilder(tiledMap);
	}


	private void extendMapBuilder(tiled.core.Map tiledMap) {
		for(TMXMapExtender extender : extras) {
			extender.extendMap(tiledMap, builder);
		}
	}

	/**
	 * Reads the map scripts, parses them into the right format and fills the
	 * builder with it.
	 * 
	 * @param tiledMap
	 */
	private void prepareGlobalMapscripts(tiled.core.Map tiledMap) {
		final String scriptStr = tiledMap.getProperties().getProperty("globalScripts");

		if (scriptStr == null) {
			builder.mapscripts = new ArrayList<>();
			return;
		}

		final List<String> scripts = Arrays.stream(scriptStr.split(",")).map((String x) -> x.trim())
				.collect(Collectors.toList());

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

		// final Properties mapProperties = tiledMap.getProperties();
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
