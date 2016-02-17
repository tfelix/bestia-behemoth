package net.bestia.maven.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import net.bestia.maven.util.FilePathHelper;
import net.bestia.maven.util.MapHelper;
import tiled.core.Map;
import tiled.core.MapLayer;

/**
 * Validates all the maps in the configured directory.
 *
 */
@Mojo(name = "validate-maps", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ValidateMaps extends AbstractMojo {

	private static final Pattern SPAWN_PATTERN = Pattern.compile("(\\d+),\\w+,\\d+,\\d+-\\d+");

	private static final Set<String> NEEDED_LAYERS;
	private static final java.util.Map<String, String> NEEDED_MAP_PROPERTIES;

	static {
		final HashSet<String> neededLayers = new HashSet<>();
		neededLayers.add("spawn");
		neededLayers.add("portals");
		neededLayers.add("scripts");
		NEEDED_LAYERS = Collections.unmodifiableSet(neededLayers);

		final HashMap<String, String> neededMapProperties = new HashMap<>();
		neededMapProperties.put("ambientSound", "No map property for 'ambientSound' (string) given.");
		neededMapProperties.put("globalScripts", "No map property for 'globalScripts' (string) given.");
		neededMapProperties.put("isPVP", "No map property for 'isPVP' (bool) given.");
		neededMapProperties.put("mapDbName", "No map property for 'mapDbName' given.");
		neededMapProperties.put("spawn", "No map property for 'spawn' given.");
		NEEDED_MAP_PROPERTIES = Collections.unmodifiableMap(neededMapProperties);
	}

	/**
	 * Directory which contains the map directories e.g. ./map
	 */
	private File mapsDirectory;

	/**
	 * Root asset directory.
	 */
	@Parameter(property = "assetRootDir", required = true)
	private File assetDirectory;

	/**
	 * Ctor. Primarly used for testing.
	 * 
	 * @param assetDirectory
	 *            Base directory for the assets.
	 */
	public ValidateMaps(File assetDirectory) {
		this.assetDirectory = assetDirectory;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		// Generate maps directory.
		mapsDirectory = Paths.get(assetDirectory.toString(), "map").toFile();

		getLog().info("Maps directory is: " + mapsDirectory.getPath());

		final File[] listing = mapsDirectory.listFiles();

		if (listing == null) {
			return;
		}

		for (File file : listing) {
			if (!file.isDirectory()) {
				getLog().warn(String.format(
						"A file is found inside the map directory: %s. There should be only folders containing the map assets.",
						file.getAbsolutePath()));
				continue;
			}

			getLog().info("Checking map: " + file.getName() + "...");

			final MapHelper mapHelper = new MapHelper(file);

			Map map;
			try {
				map = mapHelper.parseMap();
			} catch (IOException e) {
				final String longMsg = String.format("Can not parse the map file %s.", file.getName());
				throw new MojoFailureException(longMsg);
			}

			// Check if all map properties are set.
			checkMapProperties(map);

			// Check if all layers are there and named as needed.
			checkAllNecessairyLayers(map);

			// Checks if all referenced and needed files are in place.
			checkMapReferencedFile(map);
		}
	}

	/**
	 * Checks if every map property is set as needed.
	 * 
	 * @param map
	 * @throws MojoFailureException
	 */
	private void checkMapProperties(Map map) throws MojoFailureException {
		final Properties mapProps = map.getProperties();

		String errorMsgs = "";

		for (Entry<String, String> neededProp : NEEDED_MAP_PROPERTIES.entrySet()) {
			final String neededValue = neededProp.getKey();

			if (!mapProps.containsKey(neededValue)) {
				errorMsgs += neededProp.getValue() + "\n";
			}
		}

		if (!errorMsgs.isEmpty()) {
			throw new MojoFailureException(errorMsgs);
		}

		// Check for special values.
		final String spawnStr = mapProps.getProperty("spawn");
		checkSpawnStrings(spawnStr);
	}

	/**
	 * Checks if the spawn (complete) spawn string is correct.
	 * 
	 * @param spawnStr
	 * @throws MojoFailureException
	 */
	private void checkSpawnStrings(String spawnStr) throws MojoFailureException {
		if (spawnStr == null) {
			throw new MojoFailureException("Map property 'spawn' must be present (at least empty).");
		}

		final String[] spawnStrs = spawnStr.split(";");

		final Set<Integer> foundIds = new HashSet<>();

		for (String singleSpawnStr : spawnStrs) {
			// Check if the strings match the pattern.
			final Matcher m = SPAWN_PATTERN.matcher(singleSpawnStr);

			if (!m.matches()) {
				throw new MojoFailureException("Spawn string does not match the recognized pattern: " + singleSpawnStr);
			}

			final Integer id = Integer.parseInt(m.group(1));

			if (foundIds.contains(id)) {
				final String errMsg = String.format("Mob ID: %d is present twice inside the mob spawn list.", id);
				throw new MojoFailureException(errMsg);
			}

			foundIds.add(id);
		}
	}

	/**
	 * Check if the references of the map are existing.
	 * 
	 * @param map
	 * @throws MojoExecutionException
	 */
	private void checkMapReferencedFile(Map map) throws MojoFailureException {
		// Gather all mapfiles referenced by this map.
		final Properties mapProps = map.getProperties();

		final String mapName = mapProps.getProperty("mapDbName");
		final String globalScript = mapProps.getProperty("globalScripts");
		final String bgm = mapProps.getProperty("ambientSound");

		final FilePathHelper fileHelper = new FilePathHelper(assetDirectory);

		// Global Script.
		final File globScriptFile = fileHelper.getMapScript(mapName, globalScript);
		if (!globScriptFile.exists()) {
			throw new MojoFailureException(String.format("Global script file %s is missing. Referenced by map: %s",
					globScriptFile.getAbsolutePath(), mapName));
		}

		// Music.
		final File ambientMusicFile = fileHelper.getMapSound(bgm);
		if (!ambientMusicFile.exists()) {
			throw new MojoFailureException(
					String.format("Ambient music sound is missing: %s, referenced by map: %s",
							ambientMusicFile.getAbsolutePath(), mapName));
		}

		// TODO Map scripts.

	}

	/**
	 * Checks if all necessary map layer are inside the map.
	 * 
	 * @param map
	 * @throws MojoFailureException
	 */
	private void checkAllNecessairyLayers(Map map) throws MojoFailureException {

		final Set<String> layerTests = new HashSet<>(NEEDED_LAYERS);

		final Vector<MapLayer> layers = map.getLayers();

		for (MapLayer layer : layers) {
			layerTests.remove(layer.getName());
		}

		if (layerTests.size() > 0) {
			throw new MojoFailureException("Layer(s) are not present: " + layerTests.toString());
		}
	}
}
