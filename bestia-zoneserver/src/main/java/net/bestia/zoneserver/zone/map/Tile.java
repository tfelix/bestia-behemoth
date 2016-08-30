package net.bestia.zoneserver.zone.map;

import java.util.Objects;

import net.bestia.model.zone.Point;

public class Tile {
	
	private final Point position;
	private final int gid;
	private final int layer;
	
	public Tile(int layer, Point pos, int gid) {
		
		this.position = Objects.requireNonNull(pos);
		this.layer = layer;
		this.gid = gid;
	}
	
	public Point getPoint() {
		return position;
	}

	public int getGid() {
		return gid;
	}

	public int getLayer() {
		return layer;
	}
}
