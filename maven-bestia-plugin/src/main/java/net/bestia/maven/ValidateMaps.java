package net.bestia.maven;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Says "Hi" to the user.
 *
 */
@Mojo(name = "validate-maps", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ValidateMaps extends AbstractMojo {
	/**
	 * Directory which contains the map directories e.g. ./map
	 */
	@Parameter(property = "mapsDir", required = true, defaultValue = "map")
	private File mapsDirectory;

	public void execute() throws MojoExecutionException {
		
		getLog().info("Maps directory is: " + mapsDirectory.getPath());

		final File[] listing = mapsDirectory.listFiles();

		if (listing == null) {
			return;
		}

		for (File file : listing) {
			if (!file.isDirectory()) {
				continue;
			}

			getLog().info("Checking map: " + file.getName());
		}

	}
}
