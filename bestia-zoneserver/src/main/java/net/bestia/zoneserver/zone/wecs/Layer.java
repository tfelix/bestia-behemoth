package net.bestia.zoneserver.zone.wecs;

import java.util.Arrays;

/**
 * The {@link Layer} is responsible for defining a finite element layer for
 * calculations. It has a own update rate which is maintained by the
 * {@link EnvironmentManager}. Calculations performed on this layer are subject
 * to a hugh performance cost. So the operations must as be as effiecient as
 * possible.
 * 
 * @author Thomas
 *
 */
public class Layer {

	/**
	 * Side the the border of this layer.
	 *
	 */
	public enum Border {
		NORTH, EAST, SOUTH, WEST
	}

	private final float[] data;
	private final int width;
	private final int height;

	private float[][] kernel;

	public Layer(int width, int height) {

		// We need a fixed border with clamped values. Thus increasing the
		// dimensions.
		data = new float[(width + 2) * (height + 2)];

		this.width = width;
		this.height = height;
	}

	/**
	 * Fills the complete layer with a value.
	 * 
	 * @param value
	 */
	public void fillLayer(float value) {
		Arrays.fill(data, value);
	}

	/**
	 * Calculates the offset for finding the data in the array for a given x and
	 * y coordinate. The clamped borders of the layer data are taken into
	 * account.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	private int getOffset(int x, int y) {

		if (x >= width || y >= height || x < 0 || y < 0) {
			throw new IllegalArgumentException("X and Y must be between 0 and width and height of this layer.");
		}

		return (y + 1) * width + (x + 1);
	}

	/**
	 * Sets the value for the given point in the area.
	 * 
	 * @param v
	 * @param x
	 * @param y
	 */
	public void setValue(float v, int x, int y) {
		final int offset = getOffset(x, y);
		data[offset] = v;
	}

	public void tick() {
		final int kSize = (kernel.length - 1) / 2;
		
		final float[] source = Arrays.copyOf(data, data.length);

		for (int i = kSize; i < width - kSize; i++) {
			for (int j = kSize; j < height - kSize; j++) {
				
				for(int k = 0; k < kernel.length; k++) {
					for(int m = 0; m < kernel.length; m++) {
						
						data[getOffset(i, j)] += source[getOffset(i + k - kSize, j + m - kSize)] * kernel[k][m];
						
					}
				}
			}
		}
	}

	public void setValue(float[] area, int areaWidth, int areaHeight, int x, int y) {

	}

	public void setBorderValue(Border border, float value) {
		switch (border) {
		case NORTH:
			Arrays.fill(data, 0, width + 2, value);
			break;
		}
	}
}
