package net.bestia.zoneserver.script;

import java.io.File;

import org.apache.commons.io.FilenameUtils;

public class MapScript extends Script {

	private String mapDbName;
	
	public MapScript() {
		super();
	}
	
	public MapScript(String mapDbName, String name) {
		super(name);
		
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
	

}
