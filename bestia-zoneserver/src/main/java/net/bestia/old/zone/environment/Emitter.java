package net.bestia.zoneserver.zone.environment;

/**
 * This is a single point emitter. After a tick it will set the value of one or
 * more tiles in a {@link Layer} to a certain value.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Emitter {

	private final float value;
	private final int x;
	private final int y;
	
	public Emitter(int x, int y, float value) {
		this.x = x;
		this.y = y;
		this.value = value;
	}
	
	public void emit(Layer layer) {
		layer.setValue(value, x, y);
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
}
