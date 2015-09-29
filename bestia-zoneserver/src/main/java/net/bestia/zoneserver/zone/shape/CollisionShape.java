package net.bestia.zoneserver.zone.shape;

public interface CollisionShape {

	boolean collide(Vector2 s);
	boolean collide(Circle s);
	boolean collide(Rect s);
	boolean collide(CollisionShape s);
	
	public Rect getBoundingBox();
}
