package net.bestia.model.domain;

import java.util.Objects;

import javax.persistence.Entity;

/**
 * A location on the global map. None the less there might be different "maps"
 * like dungeons etc. which can be adressed here.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
public class Position {

	public final static String WORLD_MAP = "wmap";

	/**
	 * Maps are divided in areas to help memory locations. But besides this area
	 * has no meaning.
	 */
	private String area;
	private String map;

	private long x;
	private long y;

	public Position() {
		this.area = "";
		this.map = WORLD_MAP;

		this.x = 0;
		this.y = 0;
	}
	
	public Position(String mapname, String area, long x, long y) {
		
		this.map = mapname;
		this.area = area;
		
		setX(x);
		setY(y);		
	}

	public Position(long x, long y) {
		this.area = "";
		this.map = WORLD_MAP;

		setX(x);
		setY(y);
	}

	public String getArea() {
		return area;
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
		if (x < 0) {
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
		this.area = pos.getArea();
		this.map = pos.getMap();
		this.x = pos.getX();
		this.y = pos.getY();
	}

	public void setMap(String mapname) {
		this.map = Objects.requireNonNull(mapname);
	}
}
