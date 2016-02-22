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
		final URL resource = getClass().getResource("/map_mobsdesc_nok");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void test_layer_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/map_test_layer_nok");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void map_property_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/map_property_nok");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test
	public void layer_more_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/test_layer_more");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test
	public void map_ok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/test_ok");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void spawnid_nok_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/test_spawnid_nok");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
	
	@Test(expected=MojoFailureException.class)
	public void spawnid_twice_test() throws URISyntaxException, MojoExecutionException, MojoFailureException {
		final URL resource = getClass().getResource("/test_spawnid_twice");
		final File mapFolder = Paths.get(resource.toURI()).toFile();
		
		final ValidateMaps plugin = new ValidateMaps(mapFolder);
		plugin.execute();
	}
}
