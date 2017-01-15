package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

import net.bestia.model.geometry.Point;

/**
 * Position is basically a mutable version of the point.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class Position implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final String WORLD_MAP = "wmap";
	
	private String map;
	private long x;
	private long y;

	public Position() {
		this.map = WORLD_MAP;

		this.x = 0;
		this.y = 0;
	}

	public Position(String mapname, String area, long x, long y) {

		this.map = Objects.requireNonNull(mapname);
		setX(x);
		setY(y);
	}

	public Position(long x, long y) {
		this.map = WORLD_MAP;

		setX(x);
		setY(y);
	}

	public String getMap() {
		return map;
	}

	public void setX(long x) {
		if (x < 0) {
			throw new IllegalArgumentException("X can not be negative. Use positive values.");
		}

		this.x = x;
	}

	public void setY(long y) {
		if (y < 0) {
			throw new IllegalArgumentException("Y can not be negative. Use positive values.");
		}

		this.y = y;
	}

	public long getX() {
		return x;
	}

	public long getY() {
		return y;
	}

	public void set(Position pos) {
		Objects.requireNonNull(pos);
		this.map = pos.getMap();
		this.x = pos.getX();
		this.y = pos.getY();
	}

	public void setMap(String mapname) {
		this.map = Objects.requireNonNull(mapname);
	}

	@Override
	public String toString() {
		return String.format("Position[x: %d, y: %d, map: %s]", getX(), getY(), getMap());
	}

	/**
	 * Converts current position to a point.
	 * @return
	 */
	public Point toPoint() {
		return new Point(x, y);
	}
}
