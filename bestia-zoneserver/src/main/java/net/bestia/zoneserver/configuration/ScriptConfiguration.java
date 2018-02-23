package net.bestia.zoneserver.configuration;

import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.script.ScriptCache;
import net.bestia.zoneserver.script.ScriptCompiler;
import net.bestia.zoneserver.script.ScriptFileResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import javax.script.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds the script bean initialization.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
@Profile("production")
public class ScriptConfiguration {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptConfiguration.class);

	@Value("${script.path}")
	private String baseDir;

	@Autowired
	private ScriptFileResolver resolver;

	@Bean
	public ScriptCache scriptCache(ScriptCompiler compiler) throws URISyntaxException {
		final ScriptFileResolver resolver = new ScriptFileResolver(baseDir);
		final ScriptCache cache = new ScriptCache(compiler, resolver);

		final Path scriptBaseDir;

		final String classPathSuffix = "classpath:";
		if (baseDir.startsWith(classPathSuffix)) {
			final String base = baseDir.substring(classPathSuffix.length());
			final URL res = getClass().getClassLoader().getResource(base);
			final File classPath = new File(res.toURI());
			scriptBaseDir = classPath.toPath();
		} else {
			scriptBaseDir = Paths.get(baseDir);
		}

		cache.cacheFolder(scriptBaseDir);
		return cache;
	}

	/**
	 * Configures the global bindings for the script engine.
	 */
	@Bean
	public ScriptEngine scriptEngine(
			ScriptApi scriptApi,
			StaticConfigService config
	) throws ScriptException, IOException {
		final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		
		LOG.info("Starting script engine: {} (version {}).",
				engine.getFactory().getEngineName(),
				engine.getFactory().getEngineVersion());

		final Bindings bindings = engine.createBindings();
		bindings.put("SERVER_VERSION", config.getServerVersion());
		bindings.put("BAPI", scriptApi);
		engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

		final File globScriptApiFile = resolver.getGlobalScriptFile();
		try (FileReader scriptReader = new FileReader(globScriptApiFile)) {
			// Add the helper scripts.
			engine.eval(scriptReader);
		} catch (ScriptException | IOException e) {
			LOG.error("Could not compile script.", e);
			throw e;
		}

		return engine;
	}
}