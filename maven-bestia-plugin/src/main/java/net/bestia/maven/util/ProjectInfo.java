package net.bestia.maven.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ProjectInfo {
	
	final Properties properties = new Properties();
	
	public ProjectInfo() {
		try (final InputStream stream = this.getClass().getResourceAsStream("version.properties")) {
		    properties.load(stream);
		} catch (IOException e) {
			// Could not load version.
			properties.setProperty("version", "unknown");
			properties.setProperty("name", "unknown");
		}
	}
	
	public String getName() {
		return properties.getProperty("name");
	}
	
	public String getVersion() {
		return properties.getProperty("version");
	}

}
