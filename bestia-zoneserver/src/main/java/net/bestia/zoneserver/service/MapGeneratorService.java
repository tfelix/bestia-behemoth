package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tfelix.bestia.worldgen.MapMasterCallbacks;
import de.tfelix.bestia.worldgen.MapMasterGenerator;
import de.tfelix.bestia.worldgen.description.Map2DDescription;
import de.tfelix.bestia.worldgen.io.MasterCom;
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder;
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider;
import net.bestia.model.dao.MapDataDAO;
import net.bestia.zoneserver.map.MapBaseParameter;
import net.bestia.zoneserver.map.MapGeneratorConstants;

@Service
public class MapGeneratorService implements MapMasterCallbacks {

	private final static Logger LOG = LoggerFactory.getLogger(MapGeneratorService.class);

	private final AtomicBoolean isGenerating = new AtomicBoolean(false);
	private MapMasterGenerator masterGenerator = null;

	private final MapDataDAO mapDataDao;

	@Autowired
	public MapGeneratorService(MapDataDAO mapDataDao) {

		this.mapDataDao = Objects.requireNonNull(mapDataDao);
	}

	/**
	 * Generates a new map and puts it into the static save of a new map.
	 * 
	 * @param params
	 *            The basic parameter to perform the world creation with.
	 */
	public void generateMap(MapBaseParameter params, List<MasterCom> nodes) {
		if (isGenerating.get()) {
			throw new IllegalStateException("Map generation is currently in progress.");
		}

		LOG.info("Generating world with: %s", params.toString());

		LOG.info("Dropping old world from database...");
		// TODO Das droppen ggf in eigenen service auslagern, da es noch
		// komplexere behandlung der entities benötigt.
		mapDataDao.deleteAll();
		LOG.info("Old world dropped from database...");

		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		final int height = (int) params.getWorldSize().getHeight();
		final int width = (int) params.getWorldSize().getWidth();

		masterGenerator = new MapMasterGenerator(this);

		// Add all the nodes.
		nodes.forEach(masterGenerator::addNode);

		// Setup the map configuration object.
		final Map2DDescription.Builder descBuilder = new Map2DDescription.Builder();
		descBuilder.setHeight(height);
		descBuilder.setWidth(width);
		descBuilder.setPartHeight(1000);
		descBuilder.setPartWidth(1000);

		// Prepare the data.
		final NoiseVectorBuilder noiseBuilder = new NoiseVectorBuilder();
		noiseBuilder.addDimension(MapGeneratorConstants.HEIGHT_MAP, Float.class,
				new SimplexNoiseProvider(rand.nextLong()));
		noiseBuilder.addDimension(MapGeneratorConstants.RAIN_MAP, Float.class,
				new SimplexNoiseProvider(rand.nextLong()));
		noiseBuilder.addDimension(MapGeneratorConstants.MAGIC_MAP, Float.class,
				new SimplexNoiseProvider(rand.nextLong()));
		noiseBuilder.addDimension(MapGeneratorConstants.POPULATION_MAP, Float.class,
				new SimplexNoiseProvider(rand.nextLong()));
		descBuilder.setNoiseVectorBuilder(noiseBuilder);

		LOG.debug("Sending map configuration to all nodes.");

		masterGenerator.start(descBuilder.build());
	}

	@Override
	public void onWorkloadFinished(String label) {
		if (masterGenerator == null) {
			throw new IllegalStateException("Generator is null. Call generateMap() first.");
		}

		LOG.info("Map job '{}' was finished.", label);

		switch (label) {
		case MapGeneratorConstants.WORK_SCALE:
			masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_BIOMES);
			break;
		case MapGeneratorConstants.WORK_GEN_BIOMES:
			masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_TILES);
			break;
		case MapGeneratorConstants.WORK_GEN_TILES:
			LOG.info("Finished map creation.");
			masterGenerator = null;
			isGenerating.set(false);
			break;
		}
	}

	@Override
	public void onNoiseGenerationFinished() {
		if (masterGenerator == null) {
			throw new IllegalStateException("Generator is null. Call generateMap() first.");
		}

		LOG.info("Map noise was generated.");
		masterGenerator.startWorkload(MapGeneratorConstants.WORK_SCALE);
	}
}
