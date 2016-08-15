package net.bestia.zoneserver.zone.map.tmx;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.zone.Size;
import net.bestia.zoneserver.util.PackageLoader;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import tiled.io.TMXMapReader;

/**
 * Loads a TMX map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TMXMaploader {

	private final static Logger LOG = LoggerFactory.getLogger(TMXMaploader.class);

	private final TMXMapReader reader;
	private String mapFile;
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

	public void loadMap() throws IOException {

		LOG.debug("Loading mapfile: {}", mapFile);

		tiled.core.Map tiledMap;
		try {
			tiledMap = reader.readMap(mapFile);
		} catch (Exception e) {
			throw new IOException(e);
		}

		// Prepare basic data.	
		final Size mapSize = new Size(tiledMap.getHeight(), tiledMap.getWidth());
		
		final String baseName = FilenameUtils.getBaseName(mapFile);
		final String mapDbName = FilenameUtils.removeExtension(baseName);
		
		Map bestiaMap = new Map(mapDbName, mapSize);

		// Extend the map with all missing features.
		extendMapBuilder(tiledMap, null);
	}

	/**
	 * Iterates over all map extender to feed single map features into a map
	 * builder.
	 * 
	 * @param tiledMap
	 */
	private void extendMapBuilder(tiled.core.Map tiledMap, MapBuilder builder) {
		for (TMXMapExtender extender : extras) {
			extender.extendMap(tiledMap, builder);
		}
	}
}
