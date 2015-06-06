package net.bestia.zoneserver.game.worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.bestia.core.game.zone.map.Map;
import net.bestia.core.game.zone.map.Maploader;
import net.bestia.core.game.zone.map.TMXMaploader;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.game.zone.Zone;

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
		private BestiaConfiguration config;

		public ZoneLoader(List<String> zones, BestiaConfiguration configuration) {
			this.zonesToLoad = zones;
			this.config = configuration;
		}

		@Override
		public Set<Zone> call() throws Exception {

			final HashSet<Zone> loadedZones = new HashSet<>();
			
			for(String zoneName : zonesToLoad) {
				
				Maploader loader = new TMXMaploader(config.getMapfile(zoneName));
				Map.Mapbuilder builder = new Mapbuilder();
				Map map = builder.build(loader);
				Zone z = new Zone(config, zoneName, map);
				loadedZones.add(z);

			}
			
			
			return loadedZones;
		}
	}

	private final String zoneString;
	private final int nThreads;

	private final ExecutorService worker;
	private final java.util.Map<String, Zone> serverZones;
	private final BestiaConfiguration configuration;

	/**
	 * 
	 * @param config
	 *            Server configuration.
	 * @param zones
	 *            Reference to the zonelist the server is using. Loaded zones will be added to this list if finished.
	 */
	public ZoneInitLoader(BestiaConfiguration config, java.util.Map<String, Zone> zones) {
		if (config == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if (zones == null) {
			throw new IllegalArgumentException("Zones can not be null.");
		}

		configuration = config;
		zoneString = config.getProperty("zones");
		nThreads = Integer.parseInt(config.getProperty("initThreads"));

		worker = Executors.newFixedThreadPool(nThreads);
		serverZones = zones;
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
		List<String> zones = getZones();

		// Split work.
		int sizeChunk = zones.size() / nThreads;
		sizeChunk = (sizeChunk == 0) ? zones.size() : sizeChunk;

		log.trace("Loading chunks of {} in {} thread(s).", sizeChunk, nThreads);

		List<Callable<Set<Zone>>> tasks = new ArrayList<Callable<Set<Zone>>>();
		for (int i = 0; i < zones.size(); i += sizeChunk) {
			List<String> subList = zones.subList(i, i + sizeChunk);
			tasks.add(new ZoneLoader(subList, configuration));
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
			// TODO das hier anschauen? Dachte invokeAll blockt nicht...
		} finally {
			// Shutdown the executor service
			worker.shutdown();
		}
	}

	/**
	 * Splits and cleans the zone names from the configuration file.
	 * 
	 * @return Separated list of zone names.
	 */
	private List<String> getZones() {
		String[] zones = zoneString.split(",");
		for (int i = 0; i < zones.length; i++) {
			zones[i] = zones[i].trim();
		}
		return new ArrayList<String>(Arrays.asList(zones));
	}

}
