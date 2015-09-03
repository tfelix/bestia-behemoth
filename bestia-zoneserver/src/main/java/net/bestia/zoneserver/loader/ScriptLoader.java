package net.bestia.zoneserver.loader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.script.ExecutionBindings;

/**
 * TODO Kommentieren.
 * 
 * @author Thomas
 *
 */
abstract class ScriptLoader {
	
	private final File scriptBaseDir;
	
	protected final CommandContext ctx;
	
	public ScriptLoader(File baseDir, CommandContext ctx) {
		this.scriptBaseDir = baseDir;
		this.ctx = ctx;
	}

	public abstract String getKey();
	
	public abstract ExecutionBindings getExecutionBindings();
	
	public ScriptWorker getWorker() {
		final String key = getKey();
		final ScriptWorker worker = new ScriptWorker(getScriptPath(key), key);
		return worker;
	}

	public File getFolder() {
		return getScriptPath(getKey());
	}

	/**
	 * Creates the directory of the script files for a given key.
	 * 
	 * @param key
	 * @return Path to the script file directory.
	 */
	protected File getScriptPath(String key) {
		Path filePath = Paths.get(scriptBaseDir.getAbsolutePath(), "script", key);
		return filePath.toFile();
	}
}
