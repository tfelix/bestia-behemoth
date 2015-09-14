package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
	 * Flag which is used to check if the properties have changed for this
	 * entity. This is used in order to check if changes need to be send to the
	 * client.
	 */
	@Transient
	@JsonIgnore
	private boolean hasChanged = false;

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
		resetChanged();
	}

	public String getMapDbName() {
		return mapDbName;
	}

	public void setMapDbName(String mapDbName) {
		if(mapDbName == null || mapDbName.isEmpty()) {
			throw new IllegalArgumentException("MapDbName can not be null or empty.");
		}
		this.mapDbName = mapDbName;
		hasChanged = true;
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
		hasChanged = true;
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
		hasChanged = true;
	}

	@Override
	public String toString() {
		return String.format("Location[x: %d, y: %d, mabDbName: %s]", x, y,
				mapDbName);
	}

	/**
	 * Returns the flag if the entity has had changing data operations performed
	 * on its data.
	 * 
	 * @return TRUE if data has been changed or FALSE otherwise.
	 */
	public boolean hasChanged() {
		return hasChanged;
	}

	/**
	 * Resets the flag back to unchanged again.
	 */
	public void resetChanged() {
		hasChanged = false;
	}
}
