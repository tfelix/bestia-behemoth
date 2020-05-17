package net.bestia.zoneserver.map

import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.map.generator.MapGeneratorConstants
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

  companion object {
    private val LOG = LoggerFactory.getLogger(MapGenConfiguration::class.java)
    private const val MAP_GEN_DIR = "bestia-map-tempdir"
  }
}
