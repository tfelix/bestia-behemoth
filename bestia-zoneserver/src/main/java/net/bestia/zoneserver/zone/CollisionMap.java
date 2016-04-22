package net.bestia.zoneserver.zone;

import java.util.Arrays;

/**
 * This map will hold the collision data of a zone map. The collision data
 * depends on the static map data as well as collision data provided by dynamic
 * entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CollisionMap {
	
	private boolean[] collisionData;
	
	private int width;
	private int height;
	
	public CollisionMap(int width, int height) {
		
		collisionData = new boolean[width * height];
		Arrays.fill(collisionData, true);
		
		this.width = width;
		this.height = height;
	}
	
	public void setCollision(int x, int y, boolean walkable) {
		collisionData[getIndex(x, y)] = walkable;
	}
	
	public boolean isWalkable(int x, int y) {
		return collisionData[getIndex(x, y)];
	}

	private int getIndex(int x, int y) {
		return y * width + x;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
}
