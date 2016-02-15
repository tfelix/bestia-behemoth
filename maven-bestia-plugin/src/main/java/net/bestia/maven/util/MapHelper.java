package net.bestia.maven.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import net.bestia.maven.Map;
import net.bestia.maven.TMXMapReader;

/**
 * Helps to extract certain map files from map directories.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapHelper {
	
	private final File mapDir;

	
	public MapHelper(File mapDir) {
		if(mapDir == null) {
			throw new IllegalArgumentException("mapDir can not be null.");
		}
		
		this.mapDir = mapDir;
	}
	
	/**
	 * Returns the found map. If something is fishy (e.g. none or more then one
	 * .TMX file) it will throw an exception.
	 * 
	 * @param folder
	 * @return The .TMX mapfile in this folder.
	 */
	public File getMapfile() throws IOException {
		Path mapFile = null;
		try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(mapDir.toPath())) {
			for (Path path : fileStream) {
				if (path.toString().endsWith(".tmx")) {
					if (mapFile != null) {
						throw new IOException(
								"Directory contains more then one .tmx file: " + mapDir.getAbsolutePath());
					} else {
						mapFile = path;
					}
				}
			}
		}

		if (mapFile == null) {
			throw new IOException("Directory contains no .tmx file: " + mapDir.getAbsolutePath());
		}

		return mapFile.toFile();
	}
	
	public Map parseMap() throws IOException {
		final File mapeFile = getMapfile();
		try {
			final TMXMapReader reader = new TMXMapReader();
			return reader.readMap(mapFile.getAbsolutePath());
		} catch (Exception e) {
			throw new IOException("Could not parse map: " + e.getMessage(), e);
		}
	}
}
