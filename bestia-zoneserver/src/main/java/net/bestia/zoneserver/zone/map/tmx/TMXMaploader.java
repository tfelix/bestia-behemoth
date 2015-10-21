package net.bestia.zoneserver.zone.map.tmx;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.util.PackageLoader;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Maploader;
import tiled.io.TMXMapReader;

/**
 * Loads a TMX map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TMXMaploader implements Maploader {

	private final static Logger LOG = LogManager.getLogger(TMXMaploader.class);

	private final TMXMapReader reader;
	private String mapFile;
	private Map.MapBuilder builder;
	private final static Set<TMXMapExtender> extras = new HashSet<>();

	static {
		final PackageLoader<TMXMapExtender> extenderLoader = new PackageLoader<>(TMXMapExtender.class,
				"net.bestia.zoneserver.zone.map.tmx");
		for (TMXMapExtender extra : extenderLoader.getSubObjects()) {
			extras.add(extra);
		}
	}

	public TMXMaploader(File tmxMapFile) {
		this.reader = new TMXMapReader();
		this.mapFile = tmxMapFile.getAbsolutePath();
	}

	public void loadMap(Map.MapBuilder builder) throws IOException {

		LOG.debug("Loading mapfile: {}", mapFile);

		this.builder = builder;
		tiled.core.Map tiledMap;
		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			throw new IOException(e);
		}

		// Prepare basic data.
		builder.height = tiledMap.getHeight();
		builder.width = tiledMap.getWidth();
		final String baseName = FilenameUtils.getBaseName(mapFile);
		final String mapDbName = FilenameUtils.removeExtension(baseName);
		builder.mapDbName = mapDbName;

		// Extend the map with all missing features.
		extendMapBuilder(tiledMap);
	}

	/**
	 * Iterates over all map extender to feed single map features into a map
	 * builder.
	 * 
	 * @param tiledMap
	 */
	private void extendMapBuilder(tiled.core.Map tiledMap) {
		for (TMXMapExtender extender : extras) {
			extender.extendMap(tiledMap, builder);
		}
	}
}
