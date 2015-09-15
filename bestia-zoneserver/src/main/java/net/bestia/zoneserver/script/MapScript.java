package net.bestia.zoneserver.script;

public class MapScript extends Script {

	private String mapDbName;
	
	public MapScript(String mapDbName, String name) {
		super(name);
		
		this.mapDbName = mapDbName;
	}

	@Override
	public String getScriptKey() {
		return String.format("map.%s.%s", mapDbName, getName());
	}

}
