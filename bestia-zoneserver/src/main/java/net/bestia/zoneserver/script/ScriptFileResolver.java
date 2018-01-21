package net.bestia.zoneserver.script;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This class looks up a path for a script file. It is determined by the name of
 * the script and the type of the script.
 *
 * @author Thomas Felix
 */
@Component
public class ScriptFileResolver {

	private final static Logger LOG = LoggerFactory.getLogger(ScriptFileResolver.class);
	private final String scriptBasePath;

	public ScriptFileResolver(
			@Value("${script.basePath:}") String scriptBasePath
	) {

		this.scriptBasePath = scriptBasePath;
	}

	/**
	 * Returns the global script file which contains helper and API access
	 * helper.
	 *
	 * @return The global script file.
	 */
	public File getGlobalScriptFile() {
		final File globalScriptFile = getScriptFromClasspath("helper.js");
		LOG.debug("Getting global script file: {}", globalScriptFile.getAbsolutePath());
		return globalScriptFile;
	}

	/**
	 * Returns the script file path for the path and the type.
	 *
	 * @param name The name of the script.
	 * @return The path to the script.
	 */
	public File getScriptFile(String name) {

		if (!name.endsWith(".js")) {
			name += ".js";
		}

		if (!name.startsWith("/")) {
			name = "/" + name;
		}

		if (name.contains("..")) {
			throw new IllegalArgumentException("Script path can not contain ..");
		}

		if(scriptBasePath.length() == 0) {
			return getScriptFromClasspath(name);
		} else {
			return getScriptFromFolder(name);
		}
	}

	private File getScriptFromFolder(String scriptPath) {
		final Path p = Paths.get(scriptBasePath, scriptPath.split("/"));
		return p.toFile();
	}

	private File getScriptFromClasspath(String scriptPath) {
		final ClassLoader classLoader = getClass().getClassLoader();
		final Path p = Paths.get("script", scriptPath.split("/"));

		try {
			final URL scriptUrl = classLoader.getResource(p.toString());
			return new File(scriptUrl.getFile());
		} catch (NullPointerException e) {
			throw new IllegalArgumentException("File does not exist: " + p.toString(), e);
		}
	}
}
