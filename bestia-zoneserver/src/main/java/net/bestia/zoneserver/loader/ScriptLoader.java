package net.bestia.zoneserver.loader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.script.CompiledScript;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.script.Script;
import net.bestia.zoneserver.script.ScriptCompiler;
import net.bestia.zoneserver.script.ScriptManager;

/**
 * Automatically uses all available {@link ScriptLoader} in this package to load
 * scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptLoader implements Loader {

	private static final Logger log = LogManager.getLogger(ScriptLoader.class);

	private final ExecutorService worker;
	private final ScriptManager scriptManager;
	private final File baseDir;

	private final Set<String> zones;

	/**
	 * The ScriptWorker is used to load scripts of a certain type parallel at
	 * server startup.
	 * 
	 * @author Thomas Felix <thomas.felix@tfelix.de>
	 *
	 */
	private class ScriptWorker implements Callable<Map<String, CompiledScript>> {

		private final File scriptFolder;
		private final String scriptPrefix;

		/**
		 * Ctor.
		 * 
		 * @param scriptFolder
		 *            The folder to load all scripts from.
		 * @param key
		 *            The script key (e.g. item) to save them.
		 */
		public ScriptWorker(String prefix, File scriptFolder) {
			if(prefix == null || prefix.isEmpty()) {
				throw new IllegalArgumentException("Prefix can not be null or empty.");
			}
			if(scriptFolder == null || !scriptFolder.exists()) {
				throw new IllegalArgumentException("Script folder is null or does not exist.");
			}

			this.scriptFolder = scriptFolder;
			this.scriptPrefix = prefix;
		}

		@Override
		public Map<String, CompiledScript> call() throws IOException {

			final ScriptCompiler cache = new ScriptCompiler();

			log.info("Loading scripts: {}", scriptFolder.getCanonicalPath());
			
			for (File file : scriptFolder.listFiles(File::isFile)) {
				final String scriptKey = scriptPrefix + FilenameUtils.getBaseName(file.getName());
				cache.load(scriptKey, file);
			}

			return cache.getCompiledScripts();
		}
	}

	/**
	 * Ctor. The inputs are needed since these are used as bindings for the
	 * later to be executed scripts.
	 * 
	 * @param config
	 *            BestiaConfiguration object.
	 * @param ctx
	 *            A command context.
	 * @param scriptManager
	 *            The ScriptManager to be filled with the loaded and compiled
	 *            scripts.
	 */
	public ScriptLoader(BestiaConfiguration config, CommandContext ctx, ScriptManager scriptManager) {
		final int nThreads = config.getIntProperty("zone.initThreads");
		this.worker = Executors.newFixedThreadPool(nThreads);
		this.scriptManager = scriptManager;
		this.baseDir = new File(config.getProperty("zone.gameDataDir"));
		this.zones = ctx.getServer().getResponsibleZones();
	}

	/**
	 * Starts the loading process. Blocks until all loading is done and adds the
	 * instanced zones to the list given in the constructor. Its splits the work
	 * of loading into separated threads.
	 * 
	 * @throws IOException
	 *             If loading of one or more zones fails.
	 */
	public void init() throws IOException {

		log.info("Initializing: Scripts...");

		final List<Callable<Map<String, CompiledScript>>> tasks = new ArrayList<>();

		buildScriptFolder(tasks);

		// Wait for all worker to finish.
		try {
			List<Future<Map<String, CompiledScript>>> loadedScripts = worker.invokeAll(tasks);
			for (Future<Map<String, CompiledScript>> loadedScript : loadedScripts) {
				try {
					scriptManager.addScripts(loadedScript.get());
				} catch (ExecutionException e) {
					log.error("Error while loading zones.", e);
					throw new IOException("Error while loading zones.", e);
				}
			}
		} catch (InterruptedException e) {
			// no op.
		} finally {
			// Shutdown the executor service
			worker.shutdown();
		}

		// Prepare the std. bindings.

		log.info("Finished: Scripts.");
	}

	/**
	 * Creates the folder where to find the scripts for this server.
	 * 
	 * @param tasks
	 * @return
	 */
	private void buildScriptFolder(List<Callable<Map<String, CompiledScript>>> tasks) {

		tasks.add(new ScriptWorker(Script.SCRIPT_PREFIX_ITEM, Paths.get(baseDir.getAbsolutePath(),"script", "item").toFile()));
		tasks.add(new ScriptWorker(Script.SCRIPT_PREFIX_ATTACK, Paths.get(baseDir.getAbsolutePath(),"script", "attack").toFile()));

		// The maps are a bit trickier. Load scripts depending on the responsible zones.
		for (String zone : zones) {
			final File scriptFolder = Paths.get(baseDir.getAbsolutePath(),"script", "map", zone).toFile();		
			tasks.add(new ScriptWorker(Script.SCRIPT_PREFIX_MAP, scriptFolder));
		}
	}
}
