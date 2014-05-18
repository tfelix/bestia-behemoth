package net.bestia.core.game.model;

import javax.persistence.Embeddable;

/**
 * Saves a location on a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class Location {
	
	private String mapDbName;
	private int x;
	private int y;
	
	/**
	 * Ctor.
	 * Parameterless ctor which is needed for JPA.
	 */
	public Location() {
		mapDbName = "";
		x = 0;
		y = 0;
	}
	
	/**
	 * Ctor.
	 * 
	 * @param mapDbName Database name of the map.
	 * @param x X coordinate.
	 * @param y Y coordinate.
	 */
	public Location(String mapDbName, int x, int y) {
		this.setMapDbName(mapDbName);
		this.setX(x);
		this.setY(y);
	}

	public String getMapDbName() {
		return mapDbName;
	}

	public void setMapDbName(String mapDbName) {
		if(mapDbName == null || mapDbName.isEmpty()) {
			throw new IllegalArgumentException("MapDbName can not be null or empty.");
		}
		this.mapDbName = mapDbName;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		if(x < 0) {
			throw new IllegalArgumentException("Coordinates can not be negative.");
		}
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		if(y < 0) {
			throw new IllegalArgumentException("Coordinates can not be negative.");
		}
		this.y = y;
	}
	
	
}
