package net.bestia.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.io.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central configuration object it encapsulates a Properties object reads the
 * bestia config file and provides some nice access methods.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaConfiguration {

	private static final Logger LOG = LogManager.getLogger(BestiaConfiguration.class);

	private final Properties prop;
	private final BestiaVersion versionReader = new BestiaVersion();
	private boolean isLoaded = false;

	public BestiaConfiguration() {

		prop = new Properties();

	}

	/**
	 * It will try to load the default configuration file bestia.properties from
	 * the classpath.
	 * 
	 * @throws IOException
	 *             If no file could be found or read.
	 */
	public void load() throws IOException {
		final ClassLoader loader = this.getClass().getClassLoader();
		final InputStream bestiaStream = loader.getResourceAsStream("bestia.properties");
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
		final InputStreamReader reader = new InputStreamReader(new FileInputStream(propFile), Charsets.UTF_8);
		try {
			prop.load(reader);
		} finally {
			reader.close();
		}

		isLoaded = true;
	}

	/**
	 * Builds a domain name/port combination out of the values. This is useful
	 * for binding the the webserver and interserver. The build string looks
	 * like: [PREFIX][DOMAIN][:PORT] where prefix and port are optional. If not
	 * needed just pass null. The domainkey and portkey reference values in the
	 * configuration file.
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

		if (prefix == null) {
			prefix = "";
		}

		return prefix + domain + port;
	}

	public String getProperty(String key) {
		if (!checkProperty(key)) {
			throw new IllegalArgumentException("The key does not exist.");
		}
		return prop.getProperty(key);
	}

	public Integer getIntProperty(String key) {
		if (!checkProperty(key)) {
			return null;
		}
		return Integer.parseInt(prop.getProperty(key));
	}

	private boolean checkProperty(String key) {
		if (!prop.containsKey(key)) {
			LOG.warn("Key: {} was not found in the config file!", key);
			return false;
		} else {
			return true;
		}
	}

	@Deprecated
	public File getMapfile(String zoneName) {
		Path path = Paths.get(prop.getProperty("gameDataDir"), "maps", zoneName, zoneName + ".tmx");
		return path.toFile();
	}

	/**
	 * Returns the current version of the bestia game.
	 * 
	 * @return The current version.
	 */
	public String getVersion() {
		return versionReader.getVersion();
	}

	/**
	 * Checks if a configuration file has been loaded.
	 * 
	 * @return TRUE if a configuration file has been loaded. FALSE otherwise.
	 */
	public boolean isLoaded() {
		return isLoaded;
	}

	/**
	 * Sets the given value.
	 * 
	 * @param key
	 * @param value
	 */
	public void setValue(String key, Object value) {
		prop.setProperty(key, value.toString());
	}

}
