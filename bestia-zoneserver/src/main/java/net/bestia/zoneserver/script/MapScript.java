package net.bestia.zoneserver.script;

import java.io.File;

import org.apache.commons.io.FilenameUtils;

import net.bestia.zoneserver.manager.BestiaManager;

/**
 * A simple map script is triggered when a map loads. Also known as a
 * "global map script".
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScript extends Script {

	private String mapDbName;

	/**
	 * Std. Ctor for allowing the use of getScriptKeyFile().
	 */
	public MapScript() {
		super();
	}
	
	public MapScript(String mapDbName, String name, MapScriptAPI api) {
		super(name);
		
		addBinding("apiZone", api);

		this.mapDbName = mapDbName;
	}

	/**
	 * Constructor.
	 * 
	 * @param mapDbName
	 *            Name of the map.
	 * @param name
	 *            Name of the script.
	 */
	public MapScript(String mapDbName, String name, MapScriptAPI api, BestiaManager bestia) {
		super(name);
		
		addBinding("apiZone", api);
		addBinding("bestia", bestia);

		this.mapDbName = mapDbName;
	}

	@Override
	protected String getScriptPreKey() {
		return "map";
	}

	@Override
	public String getScriptKey() {
		return String.format("map.%s.%s", mapDbName, getName());
	}

	@Override
	public String getScriptKey(File scriptFile) {
		final String mapName = scriptFile.getParentFile().getName();
		final String name = FilenameUtils.getBaseName(scriptFile.getName());
		return String.format("map.%s.%s", mapName, name);
	}
	
	@Override
	public String toString() {
		return String.format("MapScript[name: %s, identKey: %s]", getName(), getScriptKey());
	}

}
