package net.bestia.zoneserver.game.zone.shape;

import net.bestia.zoneserver.game.zone.Rect;
import net.bestia.zoneserver.game.zone.Vector2;


public abstract class CollisionShape {

	public abstract boolean collide(CollisionShape shape);
	public abstract boolean collide(Vector2 p);
	public abstract Rect getBoundingBox();
}
