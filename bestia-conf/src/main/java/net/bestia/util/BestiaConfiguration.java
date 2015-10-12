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
import org.apache.commons.io.IOUtils;
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

	private Properties prop;
	private final String version;
	private boolean isLoaded = false;

	public BestiaConfiguration() {
		prop = new Properties();

		// Try to read the version string.
		final ClassLoader loader = this.getClass().getClassLoader();
		final InputStream inStream = loader.getResourceAsStream("version.properties");
		if (inStream == null) {
			this.version = "UNKNOWN (version.properties missing)";
		} else {
			String versionCode;
			try {
				versionCode = IOUtils.toString(inStream, "UTF-8");
			} catch (IOException e) {
				LOG.warn("Could not read version.properties file.", e);
				versionCode = "UNKNOWN (version.properties missing)";
			}
			this.version = versionCode;
		}
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
		checkProperty(key);
		return prop.getProperty(key);
	}

	public int getIntProperty(String key) {
		checkProperty(key);
		return Integer.parseInt(prop.getProperty(key));
	}

	private void checkProperty(String key) {
		if (!prop.containsKey(key)) {
			LOG.warn("Key: {} was not found in the config file!", key);
		}
	}

	@Deprecated
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

	/**
	 * Returns the version of the build and the name. The information is placed
	 * during build process in version.properties in the class path and read
	 * upon creation of this class. If the file is missing somehow a placeholder
	 * text is inserted.
	 * 
	 * @return The version of this software.
	 */
	public String getVersion() {
		return version;
	}

}
