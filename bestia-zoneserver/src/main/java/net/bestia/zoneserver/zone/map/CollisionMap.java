package net.bestia.zoneserver.zone.map;

import java.util.Arrays;

import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * This map will hold the collision data of a zone map. The collision data
 * depends on the static map data as well as collision data provided by dynamic
 * entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CollisionMap implements ICollisionMap {

	private boolean[] collisionData;

	private int width;
	private int height;

	public CollisionMap(int width, int height) {

		collisionData = new boolean[width * height];
		Arrays.fill(collisionData, true);

		this.width = width;
		this.height = height;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.zone.map.ICollisionMap#setCollision(int, int,
	 * boolean)
	 */
	@Override
	public void setCollision(int x, int y, boolean walkable) {
		collisionData[getIndex(x, y)] = walkable;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.zone.map.ICollisionMap#isWalkable(int, int)
	 */
	@Override
	public boolean isWalkable(int x, int y) {
		return collisionData[getIndex(x, y)];
	}

	private int getIndex(int x, int y) {
		return y * width + x;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.zone.map.ICollisionMap#getWidth()
	 */
	@Override
	public int getWidth() {
		return width;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.zone.map.ICollisionMap#getHeight()
	 */
	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public boolean isWalkable(Vector2 v) {
		return isWalkable(v.x, v.y);
	}
}
