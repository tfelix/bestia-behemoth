package net.bestia.zoneserver.configuration

import net.bestia.zoneserver.script.ScriptCache
import net.bestia.zoneserver.script.ScriptCompiler
import net.bestia.zoneserver.script.ScriptFileResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URISyntaxException
import java.nio.file.Path

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
  fun scriptCache(compiler: ScriptCompiler): ScriptCache {
    val cache = ScriptCache(compiler, resolver)

    val scriptBaseDir: Path

    /*
		final String classPathSuffix = "classpath:";
		if (config.getScriptDir().startsWith(classPathSuffix)) {
			final String base = config.getScriptDir().substring(classPathSuffix.length());
			final URL res = getClass().getClassLoader().getResource(base);
			final File classPath = new File(res.toURI());
			scriptBaseDir = classPath.toPath();
		} else {
			scriptBaseDir = Paths.get(config.getScriptDir());
		}*/

    // cache.cacheFolder(scriptBaseDir);
    return cache
  }
}