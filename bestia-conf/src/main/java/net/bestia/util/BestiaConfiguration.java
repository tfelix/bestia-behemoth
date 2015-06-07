package net.bestia.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central configuration object it encapsulates a Properties object reads the bestia config file and provides some nice
 * access methods.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaConfiguration {
	
	private static final Logger log = LogManager.getLogger(BestiaConfiguration.class);

	private Properties prop;
	private boolean isLoaded = false;

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
		isLoaded = true;
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
		isLoaded = true;
	}

	/**
	 * Builds a domain name/port combination out of the values. This is useful for binding the the webserver and
	 * interserver. The build string looks like: [PREFIX][DOMAIN][:PORT] where prefix and port are optional. If not
	 * needed just pass null. The domainkey and portkey reference values in the configuration file.
	 * 
	 * @param domainkey
	 *            Key of the config value to look up as the domain name.
	 * @param portkey
	 *            Key of the config value to look up as a port number.
	 * @param prefix
	 *            Optional. Prefix of the domain name. Usually its "tcp://".
	 * @return Build domain string.
	 */
	public String getDomainPortString(String domainkey, String portkey, String prefix) {

		String port = "";
		if (portkey != null) {
			port = ":" + getProperty(portkey);
		}

		String domain = getProperty(domainkey);
		
		if(prefix == null) {
			prefix = "";
		}

		return prefix + domain + port;
	}

	public String getProperty(String key) {
		checkProperty(key);
		return prop.getProperty(key);
	}

	public int getIntProperty(String key) {
		checkProperty(key);
		return Integer.parseInt(prop.getProperty(key));
	}
	
	private void checkProperty(String key) {
		if(!prop.containsKey(key)) {
			log.warn("Key: {} was not found in the config file!", key);
		}
	}

	public File getMapfile(String zoneName) {
		Path path = Paths.get(prop.getProperty("gameDataDir"), "maps", zoneName, zoneName + ".tmx");
		return path.toFile();
	}

	/**
	 * Checks if a configuration file has been loaded.
	 * 
	 * @return TRUE if a configuration file has been loaded. FALSE otherwise.
	 */
	public boolean isLoaded() {
		return isLoaded;
	}

}
