package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.CollisionShape;

public class MapTriggerScript {

	private CollisionShape shape;
	private String scriptName;

	public MapTriggerScript(String scriptName, CollisionShape shape) {
		this.shape = shape;
		this.scriptName = scriptName;
	}

	public CollisionShape getShape() {
		return shape;
	}

	public String getScriptName() {
		return scriptName;
	}
}