package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.Vector2;

public interface ICollisionMap {

	void setCollision(int x, int y, boolean walkable);

	boolean isWalkable(int x, int y);
	boolean isWalkable(Vector2 v);

	int getWidth();

	int getHeight();

}