package net.bestia.core.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
