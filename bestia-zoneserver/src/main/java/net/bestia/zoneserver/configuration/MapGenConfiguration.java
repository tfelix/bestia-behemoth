package net.bestia.zoneserver.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO;

/**
 * Holds configuration for the local map generator.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class MapGenConfiguration {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapGenConfiguration.class);

	private final static String MAP_GEN_DIR = "bestia-map-tempdir";

	@Bean
	public LocalFileMapGenDAO localFileMapGenDAO(@Value("${mapgen.tempDir:#{null}}") String tempDir,
			@Value("${server.name}") String nodeName) throws IOException {

		Path tempPath;

		if (tempDir == null) {
			
			final File temp = File.createTempFile("bestia-temp", null);
			final String parentTempDir = temp.getAbsoluteFile().getParentFile().getAbsolutePath();
			temp.delete();
			
			tempPath = Paths.get(parentTempDir, MAP_GEN_DIR);
			LOG.debug("Creating new temporary map directory.");
			tempPath.toFile().mkdirs();

		} else {
			
			tempPath = Paths.get(tempDir);
			if(!tempPath.toFile().exists()) {
				tempPath.toFile().mkdirs();
			}
		}
		
		LOG.debug("Using temp map dir: {}", tempPath);

		return new LocalFileMapGenDAO(nodeName, tempPath);
	}

}
