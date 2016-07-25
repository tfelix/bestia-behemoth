package net.bestia.zoneserver.zone.map;

import java.util.HashMap;

import net.bestia.zoneserver.zone.shape.Point;

public class SparseCollisionMap implements ICollisionMap {
	
	private java.util.Map<Point, Boolean> collisionData = new HashMap<>();
	
	private final int width;
	private final int height;
	
	public SparseCollisionMap(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public void setCollision(int x, int y, boolean walkable) {
		final Point v = new Point(x, y);
		if(collisionData.containsKey(v)) {
			collisionData.put(v, walkable);
		} else {
			collisionData.put(v, new Boolean(walkable));
		}
	}

	@Override
	public boolean isWalkable(int x, int y) {
		final Point v = new Point(x, y);
		if(!collisionData.containsKey(v)) {
			return true;
		} else {
			return collisionData.get(v).booleanValue();
		}
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public boolean isWalkable(Point v) {
		return isWalkable(v.x, v.y);
	}

}