package net.bestia.zoneserver.configuration;

import de.tfelix.bestia.worldgen.MapNodeGenerator;
import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO;
import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.io.MasterConnector;
import de.tfelix.bestia.worldgen.workload.MultiplyJob;
import de.tfelix.bestia.worldgen.workload.Workload;
import net.bestia.zoneserver.map.MapService;
import net.bestia.zoneserver.map.generator.MapGeneratorConstants;
import net.bestia.zoneserver.map.generator.TileGenerationJob;
import net.bestia.zoneserver.map.generator.TileSaveJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	public MapGenDAO localFileMapGenDAO(
			@Value("${mapgen.tempDir:#{null}}") String tempDir,
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
	public MapNodeGenerator mapNodeGenerator(
			StaticConfig config,
			MasterConnector connector,
			MapGenDAO dao,
			MapService mapService) {
		
		MapNodeGenerator nodeGenerator = new MapNodeGenerator(config.getServerName(), connector, dao);

		//Workload work = new Workload(MapGeneratorConstants.WORK_SCALE);
		//work.addJob(new AddJob(1.0, MapGeneratorConstants.HEIGHT_MAP));
		//work.addJob(new MultiplyJob(3500, MapGeneratorConstants.HEIGHT_MAP));
		//nodeGenerator.addWorkload(work);

		Workload work = new Workload(MapGeneratorConstants.WORK_GEN_TILES);
		work.addJob(new MultiplyJob(1500, MapGeneratorConstants.HEIGHT_MAP));
		work.addJob(new TileGenerationJob());
		work.addJob(new TileSaveJob(mapService));

		nodeGenerator.addWorkload(work);
		
		return nodeGenerator;
	}

}
