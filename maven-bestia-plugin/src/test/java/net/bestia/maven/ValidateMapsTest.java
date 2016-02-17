package net.bestia.maven;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import net.bestia.maven.map.ValidateMaps;

public class ValidateMapsTest {
	
	@Test(expected=MojoFailureException.class)
	public void mobdesc_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void test_layer_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void map_property_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test
	public void layer_more_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	// TODO Mapstruktur für die verschiedenen maps nachbilden.
	@Test
	public void map_ok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void spawnid_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void spawnid_twice_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
}
