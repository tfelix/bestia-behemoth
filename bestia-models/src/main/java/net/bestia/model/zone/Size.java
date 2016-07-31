package net.bestia.model.zone;

/**
 * Immutable size object.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Size {

	private final int width;
	private final int height;

	public Size(int width, int height) {

		this.width = width;
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	@Override
	public String toString() {
		return String.format("Size[width: %d, height: %d]", width, height);
	}
}
