package net.bestia.core.game.worker;

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

import net.bestia.core.game.zone.Zone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for taking the config, parsing the zone data and
 * then initializing the appropriate zones. In order to speed up the very hard
 * task of instancing the zones the work will be split in several threads
 * working in parallel.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ZoneInitLoader {

	private final static Logger log = LogManager
			.getLogger(ZoneInitLoader.class);

	/**
	 * Helper class to actually load the zones.
	 *
	 */
	private class ZoneLoader implements Callable<Set<Zone>> {

		public ZoneLoader(List<String> zones) {

		}

		@Override
		public Set<Zone> call() throws Exception {

			// TODO Auto-generated method stub
			return new HashSet<Zone>();
		}
	}

	private final String zoneString;
	private final int nThreads;

	private final ExecutorService worker;
	private final Set<Zone> serverZones;

	/**
	 * 
	 * @param config
	 *            Server configuration.
	 * @param zones
	 *            Reference to the zonelist the server is using. Loaded zones
	 *            will be added to this list if finished.
	 */
	public ZoneInitLoader(Properties config, Set<Zone> zones) {
		if(config == null) {
			throw new IllegalArgumentException("Config can not be null.");
		}
		if(zones == null) {
			throw new IllegalArgumentException("Zones can not be null.");
		}
		
		zoneString = config.getProperty("zones");
		nThreads = Integer.parseInt(config.getProperty("initThreads"));

		worker = Executors.newFixedThreadPool(nThreads);
		serverZones = zones;
	}

	/**
	 * Starts the loading process. Blocks until all loading is done and adds the
	 * instanced zones to the list given in the constructor.
	 * 
	 * @throws IOException
	 *             If loading of one or more zones fails.
	 */
	public void init() throws IOException {

		log.info("Start to load zones...");
		List<String> zones = getZones();

		// Split work.
		final int sizeChunk = zones.size() / nThreads;

		log.trace("Loading chunks of {} in {} thread(s).", sizeChunk, nThreads);

		List<Callable<Set<Zone>>> tasks = new ArrayList<Callable<Set<Zone>>>();
		for (int i = 0; i < zones.size(); i += sizeChunk) {
			List<String> subList = zones.subList(i, i + sizeChunk);
			tasks.add(new ZoneLoader(subList));
		}

		// Wait for all worker to finish.
		try {
			List<Future<Set<Zone>>> loadedZones = worker.invokeAll(tasks);

			for (Future<Set<Zone>> loadedZone : loadedZones) {
				try {
					serverZones.addAll(loadedZone.get());
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
