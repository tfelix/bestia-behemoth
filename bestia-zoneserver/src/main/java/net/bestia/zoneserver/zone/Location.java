package net.bestia.zoneserver.zone;

/**
 * A location on the global map. None the less there might be different "maps"
 * like dungeons etc. which can be adressed here.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Location {

	public final static String WORLD_MAP = "wmap";

	/**
	 * Maps are divided in areas to help memory locations. But besides this area
	 * has no meaning.
	 */
	private String area;
	private String map;

	private long x;
	private long y;

	public Location() {
		this.area = "";
		this.map = WORLD_MAP;

		this.x = 0;
		this.y = 0;
	}

	public Location(long x, long y) {
		this.area = "";
		this.map = WORLD_MAP;

		if (x < 0 || y < 0) {
			throw new IllegalArgumentException("Coordinates can not be negative. Use positive values.");
		}

		this.x = x;
		this.y = y;
	}
	
	public String getArea() {
		return area;
	}
	
	public String getMap() {
		return map;
	}
	
	public long getX() {
		return x;
	}
	
	public long getY() {
		return y;
	}
}
