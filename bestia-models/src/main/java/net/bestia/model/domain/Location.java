package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Saves a location on a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class Location implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@JsonProperty("mdbn")
	private String mapDbName;
	private int x;
	private int y;

	/**
	 * Ctor. Parameterless ctor which is needed for JPA.
	 */
	public Location() {
		mapDbName = "";
		x = 0;
		y = 0;
	}

	/**
	 * Ctor.
	 * 
	 * @param mapDbName
	 *            Database name of the map.
	 * @param x
	 *            X coordinate.
	 * @param y
	 *            Y coordinate.
	 */
	public Location(String mapDbName, int x, int y) {
		this.setMapDbName(mapDbName);
		this.setX(x);
		this.setY(y);
	}

	/**
	 * Copy ctor. For save creation of a copy of this location.
	 * 
	 * @param dest
	 *            Location to be copied.
	 */
	public Location(Location dest) {
		this.setMapDbName(dest.getMapDbName());
		this.setX(dest.getX());
		this.setY(dest.getY());
	}

	public String getMapDbName() {
		return mapDbName;
	}

	public void setMapDbName(String mapDbName) {
		if (mapDbName == null) {
			throw new IllegalArgumentException("MapDbName can not be null.");
		}
		this.mapDbName = mapDbName;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		if (x < 0) {
			throw new IllegalArgumentException(
					"Coordinates can not be negative.");
		}
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		if (y < 0) {
			throw new IllegalArgumentException(
					"Coordinates can not be negative.");
		}
		this.y = y;
	}

	@Override
	public String toString() {
		return String.format("Location[x: %d, y: %d, mabDbName: %s]", x, y,
				mapDbName);
	}
}
