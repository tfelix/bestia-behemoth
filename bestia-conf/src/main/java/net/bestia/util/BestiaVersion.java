package net.bestia.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads the name and the version number of the bestia project. A
 * version.properties file must exist in the classpath. (Use mavens resource
 * filtering to set the versions/data inside this file). Its a java properties
 * file with the properties:
 * <ul>
 * <li>name</li>
 * <li>version</li>
 * </ul>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaVersion {

	private static final Logger LOG = LogManager.getLogger(BestiaVersion.class);
	private final Properties properties = new Properties();

	public BestiaVersion() {
		init(null);
	}

	public BestiaVersion(File versionFile) {
		init(versionFile);
	}

	private void init(File versionFile) {
		if (properties.size() == 0) {
			// Init first.

			if (versionFile == null) {

				try (InputStream stream = this.getClass().getResourceAsStream("/version.properties")) {
					if (stream == null) {
						loadDefault();
						return;
					} else {
						properties.load(stream);
					}
				} catch (IOException e) {
					loadDefault();
				}

			} else {
				try (InputStream stream = new FileInputStream(versionFile)) {
					properties.load(stream);
				} catch (IOException e) {
					loadDefault();
				}
			}

		}
	}

	private void loadDefault() {
		// Could not load version.
		LOG.warn("version.properties was not found inside class path. Fall back to default version names.");
		properties.setProperty("version", "unknown");
		properties.setProperty("name", "unknown");
	}

	/**
	 * Returns the name of the current project.
	 * 
	 * @return The name of the current project.
	 */
	public String getName() {
		return properties.getProperty("name");
	}

	/**
	 * Returns the version of the current project.
	 * 
	 * @return The version of the current project.
	 */
	public String getVersion() {
		return properties.getProperty("version");
	}

}
