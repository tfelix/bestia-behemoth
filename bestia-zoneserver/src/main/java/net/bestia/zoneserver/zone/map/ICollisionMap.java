package net.bestia.zoneserver.zone.map;

public interface ICollisionMap {

	void setCollision(int x, int y, boolean walkable);

	boolean isWalkable(int x, int y);

	int getWidth();

	int getHeight();

}