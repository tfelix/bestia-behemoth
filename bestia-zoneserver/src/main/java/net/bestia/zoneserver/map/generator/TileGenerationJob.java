package net.bestia.zoneserver.map.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.map.MapDataPart;
import de.tfelix.bestia.worldgen.random.NoiseVector;
import de.tfelix.bestia.worldgen.workload.Job;

/**
 * Generates some sample tiles for the bestia map.
 * 
 * @author Thomas Felix
 *
 */
public class TileGenerationJob extends Job {

	private static final Logger LOG = LoggerFactory.getLogger(TileGenerationJob.class);

	private final static double WATERLEVEL = 100;

	private int waterCount = 0;
	private int landCount = 0;

	@Override
	public void foreachNoiseVector(MapGenDAO dao, MapDataPart data, NoiseVector vec) {
		if (vec.getValueDouble(MapGeneratorConstants.HEIGHT_MAP) < WATERLEVEL) {
			// Water tile.
			vec.setValue(MapGeneratorConstants.TILE_MAP, 11);
			waterCount++;
		} else {
			// Land tile.
			vec.setValue(MapGeneratorConstants.TILE_MAP, 79);
			landCount++;
		}
	}

	@Override
	public void onFinish(MapGenDAO dao, MapDataPart data) {
		LOG.debug("Finished tile generation job.");
		LOG.debug("Land tiles: {}, water tiles: {}", landCount, waterCount);
	}

	@Override
	public void onStart() {
		LOG.debug("Starting tile generation job.");
		waterCount = 0;
		landCount = 0;
	}

}
