package net.bestia.zoneserver.zone.map.tmx;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

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
		
		// Prepare basic data.
		builder.height = tiledMap.getHeight();
		builder.width = tiledMap.getWidth();
		final String baseName = FilenameUtils.getBaseName(mapFile);
		final String mapDbName = FilenameUtils.removeExtension(baseName);
		builder.mapDbName = mapDbName;
		
		// Extend the map with all missing features.
		extendMapBuilder(tiledMap);
	}


	private void extendMapBuilder(tiled.core.Map tiledMap) {
		for(TMXMapExtender extender : extras) {
			extender.extendMap(tiledMap, builder);
		}
	}
}
