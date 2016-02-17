package net.bestia.maven.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Expands single string to full, relative file paths for the given base folder.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class FilePathHelper {

	private final Path baseDir;

	private final String[] relMapSound = new String[] { "sound", "bgm" };
	private final String[] relMapScript = new String[] { "script", "map" };
	private final String[] relAttackScript = new String[] { "script", "attack" };
	private final String[] relItemScript = new String[] { "script", "item" };

	/**
	 * Ctor. Creates a helper for resolving paths to the assets subfolder
	 * directories.
	 * 
	 * @param baseDir
	 *            Base directory which contains all the game assets.
	 */
	public FilePathHelper(File baseDir) {
		if (baseDir == null) {
			throw new IllegalArgumentException("BaseDir can not be null.");
		}

		this.baseDir = baseDir.toPath();
	}

	private File getBasicFile(String name, String... subFolder) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("name can not be null.");
		}

		Path temp = Paths.get(baseDir.toString(), subFolder);
		temp = Paths.get(temp.toString(), name);

		return temp.toFile();
	}

	/**
	 * Returns the path to a map sound file.
	 * 
	 * @param name
	 *            Name of the sound.
	 * @return Path to the sound file.
	 */
	public File getMapSound(String name) {
		if(name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}
		return getBasicFile(name, relMapSound);
	}

	/**
	 * Returns the path to a attack script file.
	 * 
	 * @param name
	 *            Name of the attack.
	 * @return Path to the attack file.
	 */
	public File getAttackScript(String name) {
		if(name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}
		return getBasicFile(name + ".groovy", relAttackScript);
	}

	/**
	 * Returns the path to an item script.
	 * 
	 * @param name
	 *            Name of the script.
	 * @return Path to the item script.
	 */
	public File getItemScript(String name) {
		if(name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}
		return getBasicFile(name + ".groovy", relItemScript);
	}

	/**
	 * Returns the path to a map script.
	 * 
	 * @param mapName
	 * @param scriptName
	 * @return
	 */
	public File getMapScript(String mapName, String scriptName) {
		if (mapName == null || mapName.isEmpty()) {
			throw new IllegalArgumentException("mapName can not be null or empty.");
		}
		if (scriptName == null || scriptName.isEmpty()) {
			throw new IllegalArgumentException("scriptName can not be null or empty.");
		}

		Path temp = Paths.get(baseDir.toString(), relMapScript);
		temp = Paths.get(temp.toString(), scriptName + ".groovy");
		return temp.toFile();
	}

}
