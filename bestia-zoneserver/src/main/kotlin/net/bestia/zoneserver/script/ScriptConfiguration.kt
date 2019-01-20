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

  @Bean
  @Throws(URISyntaxException::class)
  fun scriptCache(compiler: ScriptCompiler, config: ZoneserverConfig): ScriptCache {
    val scriptBaseDir = config.scriptDir
    val isClasspath = scriptBaseDir.startsWith(CLASSPATH_PREFIX)

    val resolver = when(isClasspath) {
      true ->  ClasspathScriptFileResolver(scriptBaseDir)
      false -> FilesystemScriptFileResolver(scriptBaseDir)
    }

    return ScriptCache(compiler, resolver)
  }

  companion object {
    private const val CLASSPATH_PREFIX = "classpath:"
  }
}