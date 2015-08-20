package net.bestia.zoneserver.zone.shape;

import net.bestia.zoneserver.zone.Rect;
import net.bestia.zoneserver.zone.Vector2;


/**
 * A simple 2D bounding box shape for collision detection.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BoxCollisionShape extends CollisionShape {

	private Rect dimension;

	public BoxCollisionShape(Rect dimension) {
		if (dimension == null) {
			throw new IllegalArgumentException("Dimension can not be null.");
		}

		this.dimension = dimension;
	}
	
	public BoxCollisionShape(int x, int y, int width, int height) {
		this.dimension = new Rect(x, y, width, height);
	}

	@Override
	public boolean collide(CollisionShape shape) {
		// TODO das Problem mit den Bounding boxen hier geschickter lösen. Das
		// ist sehr inflexibel wenn neue bounding boxen hinzukommen.
		if (shape instanceof BoxCollisionShape) {
			BoxCollisionShape box = (BoxCollisionShape) shape;
			
			return (dimension.getX() < box.dimension.getX() + box.dimension.getWidth() &&
					box.dimension.getX() < dimension.getX() + dimension.getWidth() &&
					dimension.getY() < box.dimension.getY() + box.dimension.getHeight() &&
					box.dimension.getY() < dimension.getY() + dimension.getHeight());
			
		} else {
			return false;
		}
	}

	@Override
	public boolean collide(Vector2 p) {

		final int ax = p.x - dimension.getX();
		final int ay = p.y - dimension.getY();

		if (ax < 0 || ay < 0) {
			return false;
		}

		if (ax > dimension.getWidth() || ay > dimension.getHeight()) {
			return false;
		}

		return true;
	}

	@Override
	public Rect getBoundingBox() {
		return dimension;
	}


}