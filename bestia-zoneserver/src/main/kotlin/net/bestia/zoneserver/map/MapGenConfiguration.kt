package net.bestia.zoneserver.map

import de.tfelix.bestia.worldgen.MapNodeGenerator
import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO
import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.io.MasterConnector
import de.tfelix.bestia.worldgen.workload.MultiplyJob
import de.tfelix.bestia.worldgen.workload.Workload
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.map.generator.MapGeneratorConstants
import net.bestia.zoneserver.map.generator.TileGenerationJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Holds configuration for the local map generator.
 *
 * @author Thomas Felix
 */
@Configuration
class MapGenConfiguration {

  @Bean(name = ["localMapGenDao"])
  @Throws(IOException::class)
  fun localFileMapGenDAO(
          @Value("\${mapgen.tempDir:#{null}}") tempDir: String?,
          @Value("\${server.name}") nodeName: String): MapGenDAO {

    val tempPath: Path

    if (tempDir == null) {

      val temp = File.createTempFile("bestia-temp", null)
      val parentTempDir = temp.absoluteFile.parentFile.absolutePath
      temp.delete()

      tempPath = Paths.get(parentTempDir, MAP_GEN_DIR)
      LOG.debug("Creating new temporary map directory.")
      tempPath.toFile().mkdirs()

    } else {

      tempPath = Paths.get(tempDir)
      if (!tempPath.toFile().exists()) {
        tempPath.toFile().mkdirs()
      }
    }

    LOG.debug("Using temp map dir: {}", tempPath)

    return LocalFileMapGenDAO(nodeName, tempPath)
  }

  /**
   * Generates and configures the [MapNodeGenerator]. Sadly we need a
   * [MasterConnector] which is implemented via a specialized actor. We
   * need to call this method from within the actor.
   *
   * @return The used [MapNodeGenerator].
   */
  fun mapNodeGenerator(
      config: ZoneserverNodeConfig,
      connector: MasterConnector,
      dao: MapGenDAO
  ): MapNodeGenerator {

    val nodeGenerator = MapNodeGenerator(config.serverName, connector, dao)

    //Workload work = new Workload(MapGeneratorConstants.WORK_SCALE);
    //work.addJob(new AddJob(1.0, MapGeneratorConstants.HEIGHT_MAP));
    //work.addJob(new MultiplyJob(3500, MapGeneratorConstants.HEIGHT_MAP));
    //nodeGenerator.addWorkload(work);

    val work = Workload(MapGeneratorConstants.WORK_GEN_TILES)
    work.addJob(MultiplyJob(1500.0, MapGeneratorConstants.HEIGHT_MAP))
    work.addJob(TileGenerationJob())
    // work.addJob(TileSaveJob(mapService))

    nodeGenerator.addWorkload(work)

    return nodeGenerator
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(MapGenConfiguration::class.java)
    private const val MAP_GEN_DIR = "bestia-map-tempdir"
  }
}
