package net.bestia.zoneserver.configuration

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

  private val resolver = FilesystemScriptFileResolver("classpath:")

  @Bean
  @Throws(URISyntaxException::class)
  fun scriptCache(compiler: ScriptCompiler, config: ZoneserverConfig): ScriptCache {
    val cache = ScriptCache(compiler, resolver)

    val scriptBaseDir = if (config.scriptDir.startsWith(CLASSPATH_PREFIX)) {
      val base = config.scriptDir.substring(CLASSPATH_PREFIX.length)
      val res = javaClass.classLoader.getResource(base)
      val classPath = File(res.toURI())
      classPath.toPath()
    } else {
      Paths.get(config.scriptDir)
    }

    cache.cacheFolder(scriptBaseDir)

    return cache
  }

  companion object {
    private val CLASSPATH_PREFIX = "classpath:"
  }
}