package net.bestia.zoneserver.loader.script;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.script.ScriptCompiler;

/**
 * The ScriptWorker is used to load scripts of a certain type parallel at server startup.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class ScriptWorker implements Callable<ScriptWorker.ScriptLoaded> {

	private static final Logger log = LogManager.getLogger(ScriptWorker.class);

	/**
	 * Helper class to return cache with key.
	 *
	 */
	public class ScriptLoaded {

		public final ScriptCompiler cache;
		public final String key;

		public ScriptLoaded(ScriptCompiler cache, String key) {
			this.cache = cache;
			this.key = key;
		}

	}

	private final File scriptFolder;
	private final String key;

	/**
	 * Ctor.
	 * 
	 * @param scriptFolder
	 *            The folder to load all scripts from.
	 * @param key
	 *            The script key (e.g. item) to save them.
	 */
	public ScriptWorker(File scriptFolder, String key) {
		this.scriptFolder = scriptFolder;
		this.key = key;
	}

	@Override
	public ScriptLoaded call() throws IOException {

		final ScriptCompiler cache = new ScriptCompiler();

		log.info("Loading scripts: {}", scriptFolder.getCanonicalPath());

		cache.load(scriptFolder);

		return new ScriptLoaded(cache, key);
	}
}
