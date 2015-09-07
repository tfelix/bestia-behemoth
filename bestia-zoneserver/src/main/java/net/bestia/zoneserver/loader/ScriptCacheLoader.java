package net.bestia.zoneserver.loader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.script.Bindings;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.loader.ScriptWorker.ScriptLoaded;
import net.bestia.zoneserver.script.ScriptManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

/**
 * Automatically uses all available {@link ScriptLoader} in this package to load scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCacheLoader implements Loader {

	private static final Logger log = LogManager.getLogger(ScriptCacheLoader.class);

	private final ExecutorService worker;
	private final int nThreads;
	private final ScriptManager scriptManager;
	private final File baseDir;
	private final CommandContext ctx;

	/**
	 * Ctor. The inputs are needed since these are used as bindings for the later to be executed scripts.
	 * 
	 * @param config
	 *            BestiaConfiguration object.
	 * @param ctx
	 *            A command context.
	 * @param scriptManager
	 *            The ScriptManager to be filled with the loaded and compiled scripts.
	 */
	public ScriptCacheLoader(BestiaConfiguration config, CommandContext ctx, ScriptManager scriptManager) {
		this.ctx = ctx;
		this.nThreads = config.getIntProperty("zone.initThreads");
		this.worker = Executors.newFixedThreadPool(nThreads);
		this.scriptManager = scriptManager;

		this.baseDir = new File(config.getProperty("zone.gameDataDir"));
	}

	/**
	 * Starts the loading process. Blocks until all loading is done and adds the instanced zones to the list given in
	 * the constructor. Its splits the work of loading into separated threads.
	 * 
	 * @throws IOException
	 *             If loading of one or more zones fails.
	 */
	public void init() throws IOException {

		log.info("Initializing: Scripts...");

		List<Callable<ScriptWorker.ScriptLoaded>> tasks = new ArrayList<Callable<ScriptWorker.ScriptLoaded>>();

		// List all ScriptLoader instances with the script types the will load.
		final Map<String, ScriptLoader> scriptLoaderCache = new HashMap<>();

		Reflections reflections = new Reflections("net.bestia.zoneserver.loader");
		Set<Class<? extends ScriptLoader>> subTypes = reflections.getSubTypesOf(ScriptLoader.class);

		for (Class<? extends ScriptLoader> clazz : subTypes) {
			try {

				Constructor<? extends ScriptLoader> ctor = clazz.getConstructor(File.class, CommandContext.class);
				ScriptLoader instance = ctor.newInstance(baseDir, ctx);

				// Save the loader for later access for the ExecutionBindings.
				scriptLoaderCache.put(instance.getKey(), instance);

				// And setup the worker.
				tasks.add(instance.getWorker());

			} catch (Exception e) {
				log.error("Can not instanciate command handler: {}", clazz.toString(), e);
				throw new IOException("Can not load scripts.", e);
			}
		}

		// Wait for all worker to finish.
		try {
			List<Future<ScriptLoaded>> loadedScripts = worker.invokeAll(tasks);

			for (Future<ScriptLoaded> loadedScript : loadedScripts) {
				try {

					final ScriptLoaded scriptLoaded = loadedScript.get();
					final Bindings bindings = scriptLoaderCache.get(scriptLoaded.key).getExecutionBindings();

					scriptManager.addCache(scriptLoaded.key, scriptLoaded.cache, bindings);

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

		log.info("Finished: Scripts.");
	}
}
