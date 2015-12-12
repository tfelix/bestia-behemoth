package net.bestia.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.io.TMXMapReader;

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
	@Parameter(property = "mapsDir", required = true, defaultValue = "map")
	private File mapsDirectory;

	/**
	 * Ctor. Primarly used for testing.
	 */
	public ValidateMaps(File mapsDirectory) {
		this.mapsDirectory = mapsDirectory;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

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

			final File mapFile = getMapFile(file);

			final Map map = parseMap(mapFile);

			checkMapProperties(map);
			checkAllNecessairyLayers(map);
		}
	}

	private Map parseMap(File mapFile) throws MojoFailureException {
		try {
			final TMXMapReader reader = new TMXMapReader();
			return reader.readMap(mapFile.getAbsolutePath());
		} catch (Exception e) {
			throw new MojoFailureException("Could not parse map: " + e.getMessage());
		}
	}

	/**
	 * Returns the found map. If something is fishy (e.g. none or more then one
	 * .TMX file) it will throw an exception.
	 * 
	 * @param folder
	 * @return The .TMX mapfile in this folder.
	 */
	private File getMapFile(File folder) throws MojoFailureException {
		Path mapFile = null;
		try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(folder.toPath())) {
			for (Path path : fileStream) {
				if (path.toString().endsWith(".tmx")) {
					if (mapFile != null) {
						throw new MojoFailureException(
								"Directory contains more then one .tmx file: " + folder.getAbsolutePath());
					} else {
						mapFile = path;
					}
				}
			}
		} catch (IOException e) {
			throw new MojoFailureException(e.getMessage());
		}

		if (mapFile == null) {
			throw new MojoFailureException("Directory contains no .tmx file: " + folder.getAbsolutePath());
		}

		return mapFile.toFile();
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
			
			if(!m.matches()) {
				throw new MojoFailureException("Spawn string does not match the recognized pattern: " + singleSpawnStr);
			}
			
			final Integer id = Integer.parseInt(m.group(1));
			
			if(foundIds.contains(id)) {
				final String errMsg = String.format("Mob ID: %d is present twice inside the mob spawn list.", id);
				throw new MojoFailureException(errMsg);
			}
			
			foundIds.add(id);
		}
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
