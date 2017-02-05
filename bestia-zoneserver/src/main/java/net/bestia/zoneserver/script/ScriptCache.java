package net.bestia.zoneserver.script;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.script.CompiledScript;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The script cache will accept folders which contain java scripts. It will
 * start to compile the folder content and save the compiled script for later
 * use. The scripts can be reused but keep in mind that the scripts are usually
 * not thread safe and should store no persistent state.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCache {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptCache.class);

	private final Map<String, CompiledScript> cache = new HashMap<>();

	private final ScriptCompiler compiler;

	public ScriptCache(ScriptCompiler compiler) {

		this.compiler = Objects.requireNonNull(compiler);
	}

	/**
	 * Adds a folder to the script cache. It will immediatly start to compile
	 * all the scripts.
	 * 
	 * @param scriptFolder
	 *            The folder to add to the cache.
	 */
	public void addFolder(Path scriptFolder, ScriptType type) {

		LOG.info("Adding folder {} of scripts {} to script cache.", scriptFolder, type);

		// Starting to compile the scripts.
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(scriptFolder)) {
			for (Path path : directoryStream) {
				LOG.debug("Compiling script: {} (type: {})", path, type);

				final CompiledScript script = compiler.compiledScript(path.toFile());
				final String key = type.toString() + "_" + FilenameUtils.getBaseName(path.toString());

				cache.put(key, script);
			}
		} catch (IOException e) {
			LOG.error("Could not compile script.", e);
		}

	}

	/**
	 * Returns the compiled script of the given type and name.
	 * 
	 * @param type
	 *            The type of the script to be returned.
	 * @param name
	 *            The name of the scriptfile (without extention).
	 * @return The compiled script or null of no script was found.
	 */
	public CompiledScript getScript(ScriptType type, String name) {

		final String key = type.toString() + "_" + FilenameUtils.getBaseName(name);
		return cache.get(key);

	}

}
