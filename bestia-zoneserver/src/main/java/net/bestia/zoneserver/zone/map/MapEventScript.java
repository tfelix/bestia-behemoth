package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.CollisionShape;

/**
 * Holds a MapEventScript data which can be converted into a ECs component by
 * the map converter system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapEventScript {

	private final CollisionShape shape;
	private final String scriptName;
	private final int tickRate;

	public MapEventScript(String scriptName, CollisionShape shape) {
		this.shape = shape;
		this.scriptName = scriptName;
		this.tickRate = -1;
	}

	public MapEventScript(String scriptName, CollisionShape shape, int tickRate) {
		if(tickRate < 0) {
			throw new IllegalArgumentException("TickRate can not be negative.");
		}
		
		this.shape = shape;
		this.scriptName = scriptName;
		this.tickRate = tickRate;
	}

	public CollisionShape getShape() {
		return shape;
	}

	public String getScriptName() {
		return scriptName;
	}

	public int getTickRate() {
		return tickRate;
	}
}