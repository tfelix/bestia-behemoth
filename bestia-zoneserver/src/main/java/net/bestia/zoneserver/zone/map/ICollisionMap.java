package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.Point;

public interface ICollisionMap {

	void setCollision(int x, int y, boolean walkable);

	boolean isWalkable(int x, int y);
	boolean isWalkable(Point v);

	int getWidth();

	int getHeight();

}