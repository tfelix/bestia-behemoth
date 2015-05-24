package net.bestia.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Central configuration object it encapsulates a Properties object reads the bestia config file and provides some nice
 * access methods.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaConfiguration {

	private Properties prop;

	public BestiaConfiguration() {
		prop = new Properties();
	}

	/**
	 * It will try to load the default configuration file bestia.properties from the classpath.
	 * 
	 * @throws IOException
	 *             If no file could be found or read.
	 */
	public void load() throws IOException {
		ClassLoader loader = this.getClass().getClassLoader();
		InputStream bestiaStream = loader.getResourceAsStream("bestia.properties");
		prop.load(bestiaStream);
	}

	/**
	 * Loads the given file into the configuration object.
	 * 
	 * @param propFile
	 *            File which holds the configuration.
	 * @throws IOException
	 *             If the file could not be found or read.
	 */
	public void load(File propFile) throws IOException {
		prop.load(new FileReader(propFile));
	}

	public String getProperty(String key) {
		return prop.getProperty(key);
	}

	public int getIntProperty(String key) {
		return Integer.parseInt(prop.getProperty(key));
	}

	public File getMapfile(String zoneName) {
		Path path = Paths.get(prop.getProperty("gameDataDir"), "maps", zoneName, zoneName + ".tmx");
		return path.toFile();
	}

}
