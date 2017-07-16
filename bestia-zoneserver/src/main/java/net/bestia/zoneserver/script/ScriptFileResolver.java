package net.bestia.zoneserver.script;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class looks up a path for a script file. It is determined by the name of
 * the script and the type of the script.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptFileResolver {

	private final static Logger LOG = LoggerFactory.getLogger(ScriptFileResolver.class);

	private final ClassLoader classLoader = getClass().getClassLoader();

	public ScriptFileResolver() {
		// no op.
	}

	/**
	 * Returns the global script file which contains helper and API access
	 * helper.
	 * 
	 * @return The global script file.
	 */
	public File getGlobalScriptFile() {
		final File globalScriptFile = new File(classLoader.getResource("script/api.js").getFile());
		LOG.debug("Getting global script file: {}", globalScriptFile.getAbsolutePath());
		return globalScriptFile;
	}

	/**
	 * Returns the script file path for the path and the type.
	 * 
	 * @param name
	 *            The name of the script.
	 * @param type
	 *            The script type.
	 * @return The path to the script.
	 */
	public File getScriptFile(String name, ScriptType type) {

		if (!name.endsWith(".js")) {
			name += ".js";
		}
		
		final String endPath;
		
		if(type == ScriptType.NONE) {
			endPath = String.format("script/%s", name);
		} else {
			endPath = String.format("script/%s/%s", type.toString().toLowerCase(), name);
		}
		
		try {
			final File scriptFile = new File(classLoader.getResource(endPath).getFile());
			LOG.trace("Getting global script file: {}", scriptFile.getAbsolutePath());
			return scriptFile;
		} catch (NullPointerException e) {
			throw new IllegalArgumentException("File does not exist: " + endPath, e);
		}
	}
}
