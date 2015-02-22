package net.bestia.core.game.zone;

/**
 * A simple 2D bounding box shape for collision detection.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BoxCollisionShape extends CollisionShape {

	private Dimension dimension;

	public BoxCollisionShape(Dimension dimension) {
		if (dimension == null) {
			throw new IllegalArgumentException("Dimension can not be null.");
		}

		this.dimension = dimension;
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
	public boolean collide(Point p) {

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
	public Dimension getBoundingBox() {
		return dimension;
	}
}
