package net.bestia.zoneserver.game.zone;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Dimension {

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	/**
	 * Ctor. Createa a bounding box at x and y equals 0.
	 * 
	 * @param width
	 *            Width
	 * @param height
	 *            Height
	 */
	public Dimension(int width, int height) {
		this.x = 0;
		this.y = 0;
		this.width = width;
		this.height = height;
	}

	/**
	 * Ctor.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public Dimension(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public Dimension(Point cords, Point size) {
		this.x = cords.x;
		this.y = cords.y;
		this.width = size.x;
		this.height = size.y;
	}

	public String toString() {
		return String.format("Dimension[x: %d, y: %d, width: %d, height: %d]", x, y, width, height);
	}

	@Override
	public int hashCode() {
		return 31 * width + 11 * height + 7 * x + y;
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof Dimension)) {
			return false;
		}

		Dimension p = (Dimension) o;
		return width == p.width && height == p.height && x == p.x && y == p.y;
	}

	/**
	 * Returns the width.
	 * 
	 * @return Width.
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Returns the height.
	 * 
	 * @return Height.
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Returns the x value. Offset of the box from origin.
	 * 
	 * @return x
	 */
	public int getX() {
		return x;
	}

	/**
	 * Returns the y value. Offset of the box from origin.
	 * 
	 * @return y
	 */
	public int getY() {
		return y;
	}
}