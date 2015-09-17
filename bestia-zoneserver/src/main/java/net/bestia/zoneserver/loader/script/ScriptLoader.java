package net.bestia.zoneserver.loader.script;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import net.bestia.zoneserver.command.CommandContext;

/**
 * The {@link ScriptLoader} will create worker which will do the actual loading of the scripts. Therefore it provides a
 * sub-folder to load (e.g. "item") and the permanent script bindings which will be used during executions of scripts
 * from this category.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
abstract class ScriptLoader {

	private final File scriptBaseDir;
	private final Bindings bindings = new SimpleBindings();

	protected final CommandContext ctx;

	public ScriptLoader(File baseDir, CommandContext ctx) {
		this.scriptBaseDir = baseDir;
		this.ctx = ctx;
	}

	/**
	 * The key (subfolder) of the scripts. E.g. "item".
	 * 
	 * @return Key (subfolder) of the scripts.
	 */
	public abstract String getKey();

	/**
	 * Helper method to add new bindings to this kind of script.
	 * 
	 * @param name
	 *            Name of the variable to be used as binding later in the script upon call.
	 * @param object
	 *            The variable to bind.
	 */
	protected void addBinding(String name, Object object) {
		bindings.put(name, object);
	}

	/**
	 * The permanent bindings for these scripts.
	 * 
	 * @return Permanent script bindings.
	 */
	public Bindings getExecutionBindings() {
		return bindings;
	}

	/**
	 * Returns the worker who will do the actual loading.
	 * 
	 * @return {@link ScriptWorker} who will do the actual loading.
	 */
	public ScriptWorker getWorker() {
		final String key = getKey();
		final ScriptWorker worker = new ScriptWorker(getScriptPath(key), key);
		return worker;
	}

	/**
	 * The folder where to find the scripts of this category.
	 * 
	 * @return The folder of the scripts.
	 */
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
