package net.bestia.zoneserver.game.zone;


public abstract class CollisionShape {

	public abstract boolean collide(CollisionShape shape);
	public abstract boolean collide(Point p);
	public abstract Dimension getBoundingBox();
}
