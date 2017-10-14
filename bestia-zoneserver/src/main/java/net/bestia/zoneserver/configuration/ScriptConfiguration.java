package net.bestia.zoneserver.configuration;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

import net.bestia.messages.MessageApi;
import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.script.ScriptCache;
import net.bestia.zoneserver.script.ScriptCompiler;
import net.bestia.zoneserver.script.ScriptFileResolver;
import net.bestia.zoneserver.script.ScriptType;

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

	private static class ScriptDir {
		public final String subDir;
		public final ScriptType type;

		public ScriptDir(String subDir, ScriptType type) {

			this.subDir = subDir;
			this.type = type;
		}
	}

	@Value("${server.scriptDir}")
	private String baseDir;

	private static final List<ScriptDir> SCRIPT_SUB_DIRS = Arrays.asList(
			new ScriptDir("attack", ScriptType.ATTACK),
			new ScriptDir("map", ScriptType.MAP),
			new ScriptDir("item", ScriptType.ITEM),
			new ScriptDir("statuseffect", ScriptType.STATUS_EFFECT));

	@Bean
	public ScriptCache scriptCache(ScriptCompiler compiler) throws URISyntaxException {
		final ScriptCache cache = new ScriptCache(compiler, new ScriptFileResolver());

		final Path scriptBaseDir;

		if (baseDir.startsWith("classpath:")) {
			final String base = baseDir.substring(10);
			URL res = getClass().getClassLoader().getResource(base);
			final File classPath = new File(res.toURI());
			scriptBaseDir = classPath.toPath();
		} else {
			scriptBaseDir = Paths.get(baseDir);
		}

		// Load all the scripts.
		for (ScriptDir subDir : SCRIPT_SUB_DIRS) {

			final Path scriptDir = Paths.get(scriptBaseDir.toString(), subDir.subDir);
			cache.addFolder(scriptDir, subDir.type);
		}

		return cache;
	}

	/**
	 * Configures the global bindings for the script engine.
	 */
	@Bean
	public ScriptEngine scriptEngine(MessageApi akkaApi, ScriptApi scriptApi, StaticConfigService config) {
		final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		
		LOG.info("Starting script engine: {} (version {}).",
				engine.getFactory().getEngineName(),
				engine.getFactory().getEngineVersion());

		final Bindings bindings = engine.createBindings();

		bindings.put("SERVER_VERSION", config.getServerVersion());
		bindings.put("BAPI", scriptApi);

		engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

		ScriptFileResolver resolver = new ScriptFileResolver();
		final File globScriptApiFile = resolver.getGlobalScriptFile();

		try (FileReader scriptReader = new FileReader(globScriptApiFile)) {

			// Add the helper scripts.
			engine.eval(scriptReader);

		} catch (ScriptException | IOException e) {
			LOG.error("Could not compile script.", e);
			return null;
		}

		return engine;
	}

}
