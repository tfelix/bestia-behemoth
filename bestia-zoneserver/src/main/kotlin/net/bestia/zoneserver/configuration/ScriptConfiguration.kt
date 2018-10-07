package net.bestia.zoneserver.configuration

import net.bestia.zoneserver.script.ClasspathScriptFileResolver
import net.bestia.zoneserver.script.ScriptCache
import net.bestia.zoneserver.script.ScriptCompiler
import net.bestia.zoneserver.script.FilesystemScriptFileResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.net.URISyntaxException
import java.nio.file.Paths

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