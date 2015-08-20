package net.bestia.zoneserver.zone.shape;

import net.bestia.zoneserver.zone.Rect;
import net.bestia.zoneserver.zone.Vector2;


public abstract class CollisionShape {

	public abstract boolean collide(CollisionShape shape);
	public abstract boolean collide(Vector2 p);
	public abstract Rect getBoundingBox();
}
