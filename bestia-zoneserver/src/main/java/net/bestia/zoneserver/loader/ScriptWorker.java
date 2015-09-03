package net.bestia.zoneserver.loader;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.script.ScriptCache;

/**
 * TODO Kommentieren.
 * @author Thomas
 *
 */
class ScriptWorker implements Callable<ScriptWorker.ScriptLoaded> {

	private static final Logger log = LogManager.getLogger(ScriptWorker.class);
	
	/**
	 * Helper class to return cache with key.
	 *
	 */
	public class ScriptLoaded {

		public final ScriptCache cache;
		public final String key;

		public ScriptLoaded(ScriptCache cache, String key) {
			this.cache = cache;
			this.key = key;
		}

	}
	
	private final File scriptFolder;
	private final String key;

	public ScriptWorker(File scriptFolder, String key) {
		this.scriptFolder = scriptFolder;
		this.key = key;
	}

	@Override
	public ScriptLoaded call() throws IOException {

		final ScriptCache cache = new ScriptCache();

		log.info("Loading scripts: {}", scriptFolder.getCanonicalPath());

		cache.load(scriptFolder);

		return new ScriptLoaded(cache, key);
	}
}
