package net.bestia.zoneserver.configuration

import net.bestia.zoneserver.script.ScriptCache
import net.bestia.zoneserver.script.ScriptCompiler
import net.bestia.zoneserver.script.ScriptFileResolver
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

  private val resolver = ScriptFileResolver("classpath:")

  @Bean
  @Throws(URISyntaxException::class)
  fun scriptCache(compiler: ScriptCompiler, config: ZoneserverConfig): ScriptCache {
    val cache = ScriptCache(compiler, resolver)
    val classPathSuffix = "classpath:"

    val scriptBaseDir = if (config.scriptDir.startsWith(classPathSuffix)) {
      val base = config.scriptDir.substring(classPathSuffix.length)
      val res = javaClass.classLoader.getResource(base)
      val classPath = File(res.toURI())
      classPath.toPath()
    } else {
      Paths.get(config.scriptDir)
    }

    cache.cacheFolder(scriptBaseDir)

    return cache
  }
}