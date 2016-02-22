package net.bestia.maven.map;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import net.bestia.maven.util.MapHelper;

/**
 * This class re-creates the JSON maps from the TMX reference maps. The JSON
 * maps are used by the clients in order to render the maps. TMX format is used
 * to keep the maps in sync with the server. Thus it is crucial to keep both
 * versions ins sync with each other.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Mojo(name = "create-json-maps", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class CreateJsonMaps extends AbstractMojo {
	
	/**
	 * Directory which contains the map directories e.g. ./map
	 */
	@Parameter(property = "mapsDir", required = true, defaultValue = "map")
	private File mapsDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().debug("Maps directory is: " + mapsDirectory.getPath());
		
		// Read all TMX mapfiles in subfolder.
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

			getLog().info("Creating JSON map: " + file.getName() + ".");
			
			final MapHelper helper = new MapHelper(file);

			// Parse all TMX mapfiles and write JSON files into the subfolder.
			// TODO JSON Datei schreiben.
		}
	}
}
