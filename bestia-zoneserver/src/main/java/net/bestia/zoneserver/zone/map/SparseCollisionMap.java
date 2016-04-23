package net.bestia.zoneserver.zone.map;

import java.util.HashMap;

import net.bestia.zoneserver.zone.shape.Vector2;

public class SparseCollisionMap implements ICollisionMap {
	
	private java.util.Map<Vector2, Boolean> collisionData = new HashMap<>();
	
	private final int width;
	private final int height;
	
	public SparseCollisionMap(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public void setCollision(int x, int y, boolean walkable) {
		final Vector2 v = new Vector2(x, y);
		if(collisionData.containsKey(v)) {
			collisionData.put(v, walkable);
		} else {
			collisionData.put(v, new Boolean(walkable));
		}
	}

	@Override
	public boolean isWalkable(int x, int y) {
		final Vector2 v = new Vector2(x, y);
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

}
