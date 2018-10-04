package net.bestia.zoneserver.map.generator;

import de.tfelix.bestia.worldgen.MapMasterCallbacks;
import de.tfelix.bestia.worldgen.MapMasterGenerator;
import de.tfelix.bestia.worldgen.description.Map2DDescription;
import de.tfelix.bestia.worldgen.io.NodeConnector;
import de.tfelix.bestia.worldgen.message.WorkstateMessage;
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder;
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider;
import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MapGeneratorMasterService implements MapMasterCallbacks {

	private final static Logger LOG = LoggerFactory.getLogger(MapGeneratorMasterService.class);

	private final AtomicBoolean isGenerating = new AtomicBoolean(false);
	private MapMasterGenerator masterGenerator = null;

	private final MapDataDAO mapDataDao;
	private final MapParameterDAO mapParamDao;

	private Runnable onFinishCallback = null;

	@Autowired
	public MapGeneratorMasterService(MapDataDAO mapDataDao, MapParameterDAO mapParamDao) {

		this.mapDataDao = Objects.requireNonNull(mapDataDao);
		this.mapParamDao = Objects.requireNonNull(mapParamDao);
	}

	/**
	 * Generates a new map and puts it into the static save of a new map.
	 *
	 * @param params
	 *            The basic parameter to perform the world creation with.
	 */
	public void generateMap(MapParameter params, List<NodeConnector> nodes) {
		if (!isGenerating.compareAndSet(false, true)) {
			throw new IllegalStateException("Map generation is currently in progress.");
		}

		LOG.info("Generating world with: {}", params.toString());

		mapParamDao.save(params);

		LOG.info("Dropping old world from database...");
		// FIXME Das droppen ggf in eigenen service auslagern, da es noch
		// komplexere behandlung der entities benötigt.
		mapDataDao.deleteAll();
		LOG.info("Old world dropped from database.");

		final ThreadLocalRandom rand = ThreadLocalRandom.current();

		final long height = params.getWorldSize().getHeight();
		final long width = params.getWorldSize().getWidth();

		masterGenerator = new MapMasterGenerator(this);

		// Add all the nodes.
		nodes.forEach(masterGenerator::addNode);

		// Setup the map configuration object.
		final Map2DDescription.Builder descBuilder = new Map2DDescription.Builder();
		descBuilder.setHeight(height);
		descBuilder.setWidth(width);
		descBuilder.setPartHeight(100);
		descBuilder.setPartWidth(100);

		// Prepare the data.
		final NoiseVectorBuilder noiseBuilder = new NoiseVectorBuilder();
		noiseBuilder.addDimension(MapGeneratorConstants.HEIGHT_MAP,
				Float.class,
				new SimplexNoiseProvider(rand.nextLong(), 0.0001));

		noiseBuilder.addDimension(MapGeneratorConstants.RAIN_MAP,
				Float.class,
				new SimplexNoiseProvider(rand.nextLong(), 0.0001));
		noiseBuilder.addDimension(MapGeneratorConstants.MAGIC_MAP,
				Float.class,
				new SimplexNoiseProvider(rand.nextLong(), 0.0001));
		noiseBuilder.addDimension(MapGeneratorConstants.POPULATION_MAP,
				Float.class,
				new SimplexNoiseProvider(rand.nextLong(), 0.0001));

		descBuilder.setNoiseVectorBuilder(noiseBuilder);

		LOG.debug("Sending map configuration to all nodes.");

		masterGenerator.start(descBuilder.build());
	}

	/**
	 * Sets a callback this is executed after the mapgeneration has finished.
	 *
	 * @param onFinishCallback
	 */
	public void setOnFinishCallback(Runnable onFinishCallback) {
		this.onFinishCallback = onFinishCallback;
	}

	/**
	 * Helper method which will give the master the current state of the nodes.
	 * The {@link MapMasterGenerator} class will take care if keeping track
	 * about the origins.
	 *
	 * @param workstate
	 *            The workstate reported by the client.
	 */
	public void consumeNodeMessage(WorkstateMessage workstate) {
		if (masterGenerator == null) {
			LOG.warn("Inbound message even if no map generation is in place.");
			return;
		}

		masterGenerator.consumeNodeMessage(workstate);
	}

	@Override
	public void onWorkloadFinished(String label) {
		if (masterGenerator == null) {
			throw new IllegalStateException("Generator is null. Call generateMap() first.");
		}

		LOG.info("Map job '{}' was finished.", label);

		// TODO We currently only use one test job which also saves the map to
		// the db.

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

			try {
				if (onFinishCallback != null) {
					onFinishCallback.run();
				}
			} catch (Exception e) {
				LOG.warn("Exception in callback handler.", e);
			}

			break;
		default:
			LOG.warn("Unknown workload label: {}.", label);
			return;
		}
	}

	@Override
	public void onNoiseGenerationFinished() {
		if (masterGenerator == null) {
			throw new IllegalStateException("Generator is null. Call generateMap() first.");
		}

		LOG.info("Map noise was generated.");
		masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_TILES);
	}
}
