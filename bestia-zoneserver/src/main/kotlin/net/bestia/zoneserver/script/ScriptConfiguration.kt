package net.bestia.zoneserver.script

import net.bestia.zoneserver.config.ZoneserverConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URISyntaxException

/**
 * Holds the script bean initialization.
 *
 * @author Thomas Felix
 */
@Configuration
class ScriptConfiguration {

  companion object {
    private const val CLASSPATH_PREFIX = "classpath:"
  }
}