package net.bestia.zoneserver.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO;
import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.io.MasterConnector;
import de.tfelix.bestia.worldgen.map.MapDataPart;
import de.tfelix.bestia.worldgen.random.NoiseVector;
import de.tfelix.bestia.worldgen.workload.Job;
import de.tfelix.bestia.worldgen.workload.MultiplyJob;
import de.tfelix.bestia.worldgen.workload.Workload;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.MapDataDTO;
import net.bestia.zoneserver.map.MapGeneratorConstants;
import net.bestia.zoneserver.map.MapService;

/**
 * Holds configuration for the local map generator.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class MapGenConfiguration {

	private static final Logger LOG = LoggerFactory.getLogger(MapGenConfiguration.class);

	private final static String MAP_GEN_DIR = "bestia-map-tempdir";

	@Bean(name = "localMapGenDao")
	public MapGenDAO localFileMapGenDAO(@Value("${mapgen.tempDir:#{null}}") String tempDir,
			@Value("${server.name}") String nodeName) throws IOException {

		Path tempPath;

		if (tempDir == null) {

			final File temp = File.createTempFile("bestia-temp", null);
			final String parentTempDir = temp.getAbsoluteFile().getParentFile().getAbsolutePath();
			temp.delete();

			tempPath = Paths.get(parentTempDir, MAP_GEN_DIR);
			LOG.debug("Creating new temporary map directory.");
			tempPath.toFile().mkdirs();

		} else {

			tempPath = Paths.get(tempDir);
			if (!tempPath.toFile().exists()) {
				tempPath.toFile().mkdirs();
			}
		}

		LOG.debug("Using temp map dir: {}", tempPath);

		return new LocalFileMapGenDAO(nodeName, tempPath);
	}

	/**
	 * Generates and configures the {@link MapNodeGenerator}. Sadly we need a
	 * {@link MasterConnector} which is implemented via a specialized actor. We
	 * need to call this method from within the actor.
	 * 
	 * @return The used {@link MapNodeGenerator}.
	 */
	public MapNodeGenerator mapNodeGenerator(StaticConfigurationService config,
			MasterConnector connector,
			MapGenDAO dao,
			MapService mapService) {
		MapNodeGenerator nodeGenerator = new MapNodeGenerator(config.getServerName(), connector, dao);

		Workload work = new Workload(MapGeneratorConstants.WORK_SCALE);
		work.addJob(new MultiplyJob(3500, MapGeneratorConstants.HEIGHT_MAP));
		nodeGenerator.addWorkload(work);

		work = new Workload(MapGeneratorConstants.WORK_GEN_TILES);

		work.addJob(new Job() {

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
					vec.setValue(MapGeneratorConstants.TILE_MAP, 80);
					landCount++;
				}
			}

			@Override
			public void onFinish(MapGenDAO dao, MapDataPart data) {
				LOG.debug("Finished map part. Land tiles: {}, water tiles: {}", landCount, waterCount);
			}

			@Override
			public void onStart() {
				waterCount = 0;
				landCount = 0;
			}
		});

		work.addJob(new Job() {
			@Override
			public void foreachNoiseVector(MapGenDAO dao, MapDataPart data, NoiseVector vec) {
				// Now the tiles must be saved.
				LOG.info("Map is now saved to the database.");
				
				// TODO Das hier ist nur provisorisch um das system grundsätzlich zu testen.
				for(int x = 0; x < 10; x++) {
					for(int y = 0; y < 10; y++) {
						
						Rect rect = new Rect(x * 100, y * 100, 100, 100);
						MapDataDTO mapDataDto = new MapDataDTO(rect);
						
						for(int x2 = x * 100; x2 < x * 100 + 100; x2++) {
							for(int y2 = y * 100; y2 < y * 100 + 100; y2++) {
								
								mapDataDto.putGroundLayer(x2, y2, 80);
								
							}
						}
						
						mapService.saveMapData(mapDataDto);
					}
				}				
			}
		});

		nodeGenerator.addWorkload(work);
		return nodeGenerator;
	}

}
