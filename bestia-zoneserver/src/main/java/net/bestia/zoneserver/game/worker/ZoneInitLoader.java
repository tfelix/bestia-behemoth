package net.bestia.zoneserver.game.worker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.game.zone.Zone;
import net.bestia.zoneserver.game.zone.map.Map;
import net.bestia.zoneserver.game.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.game.zone.map.Maploader;
import net.bestia.zoneserver.game.zone.map.TMXMaploader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for taking the config, parsing the zone data and then initializing the appropriate zones.
 * In order to speed up the very hard task of instancing the zones the work will be split in several threads working in
 * parallel.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ZoneInitLoader {

	private final static Logger log = LogManager.getLogger(ZoneInitLoader.class);

	/**
	 * Helper class to actually load the zones.
	 *
	 */
	private class ZoneLoader implements Callable<Set<Zone>> {

		private List<String> zonesToLoad;
		private File mapDataDir;

		public ZoneLoader(List<String> zones, File mapDataDir) {
			this.zonesToLoad = zones;
			this.mapDataDir = mapDataDir;
		}

		/**
		 * Returns the path to the map file which can be parsed in order to generate our static map data.
		 * 
		 * @param zoneName
		 *            MapDBName of the map to load.
		 * @return File pointing to the mapfile.
		 */
		private File getMapFile(String zoneName) {
			return Paths.get(mapDataDir.getAbsolutePath(), "map", zoneName, zoneName + ".tmx").toFile();
		}

		@Override
		public Set<Zone> call() throws IOException {

			final HashSet<Zone> loadedZones = new HashSet<>();

			for (String zoneName : zonesToLoad) {

				final File mapFile = getMapFile(zoneName);
				final Zone z = loadZone(zoneName, mapFile);
				loadedZones.add(z);

			}

			return loadedZones;
		}

		private Zone loadZone(String zoneName, File mapFile) throws IOException {
			final Maploader loader = new TMXMaploader(mapFile);
			final Map.MapBuilder builder = new MapBuilder();

			builder.load(loader);

			Zone z = new Zone(config, builder.build());

			return z;
		}
	}

	private final ExecutorService worker;
	private final java.util.Map<String, Zone> serverZones;
	private final Set<String> zoneNames;
	private final int nThreads;
	private final BestiaConfiguration config;
	private final File mapDataDir;

	/**
	 * 
	 * @param zoneNames
	 *            Name of the zones to load.
	 * @param mapDataDir
	 *            Directory where to find the zone datafiles.
	 * @param nThreads
	 *            Number of threads to use for loading.
	 * @param zones
	 *            Reference to the zonelist the server is using. Loaded zones will be added to this list if finished.
	 */
	public ZoneInitLoader(Set<String> zoneNames, BestiaConfiguration config, java.util.Map<String, Zone> zones) {
		if (config == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if (zoneNames == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if (zones == null) {
			throw new IllegalArgumentException("Zone reference can not be null.");
		}

		this.nThreads = config.getIntProperty("zone.initThreads");
		this.zoneNames = zoneNames;
		this.worker = Executors.newFixedThreadPool(nThreads);
		this.serverZones = zones;
		this.mapDataDir = new File(config.getProperty("zone.gameDataDir"));
		this.config = config;
	}

	/**
	 * Starts the loading process. Blocks until all loading is done and adds the instanced zones to the list given in
	 * the constructor. Its splits the work of loading into separated threads.
	 * 
	 * @throws IOException
	 *             If loading of one or more zones fails.
	 */
	public void init() throws IOException {

		log.info("Start to load zones...");

		// Split work.
		int sizeChunk = zoneNames.size() / nThreads;
		sizeChunk = (sizeChunk == 0) ? zoneNames.size() : sizeChunk;

		log.trace("Loading chunks of {} in {} thread(s).", sizeChunk, nThreads);

		List<Callable<Set<Zone>>> tasks = new ArrayList<Callable<Set<Zone>>>();

		List<String> zoneList = new ArrayList<String>();
		zoneList.addAll(zoneNames);

		for (int i = 0; i < zoneNames.size(); i += sizeChunk) {
			List<String> subList = zoneList.subList(i, i + sizeChunk);
			tasks.add(new ZoneLoader(subList, mapDataDir));
		}

		// Wait for all worker to finish.
		try {
			List<Future<Set<Zone>>> loadedZones = worker.invokeAll(tasks);

			for (Future<Set<Zone>> loadedZone : loadedZones) {
				try {
					for (Zone z : loadedZone.get()) {
						serverZones.put(z.getName(), z);
					}
				} catch (ExecutionException e) {
					log.error("Error while loading zones.", e);
					throw new IOException("Error while loading zones.", e);
				}
			}

		} catch (InterruptedException e) {
			// no op.
		} finally {
			// Shutdown the executor service
			worker.shutdown();
		}
	}
}
