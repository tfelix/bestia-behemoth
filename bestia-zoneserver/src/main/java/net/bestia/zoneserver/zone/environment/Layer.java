package net.bestia.zoneserver.zone.environment;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The {@link Layer} is responsible for defining a finite element layer for
 * calculations. It has a own update rate which is maintained by the
 * {@link EnvironmentManager}. Calculations performed on this layer are subject
 * to a hugh performance cost. So the operations must as be as efficient as
 * possible. The access to the layer must be threadsafe since the layer data is
 * usually worked upon in a background thread. Nether the less another thread
 * might access the data so there need to be marshalling.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
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

	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock readLock = readWriteLock.readLock();
	private final Lock writeLock = readWriteLock.writeLock();

	private final float[] data;
	private final int width;
	private final int height;

	private float maxDelta = 0.f;
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
		writeLock.lock();
		try {
			Arrays.fill(data, value);
		} finally {
			writeLock.unlock();
		}

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
		writeLock.lock();
		try {
			final int offset = getOffset(x, y);
			data[offset] = v;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Returns the value for the given point.
	 * 
	 * @param x
	 * @param y
	 * @return The value at this coordiantes.
	 */
	public float getValue(int x, int y) {
		readLock.lock();
		try {
			final int offset = getOffset(x, y);
			return data[offset];
		} finally {
			readLock.unlock();
		}
	}

	public void tick() {
		final int kSize = (kernel.length - 1) / 2;

		writeLock.lock();
		try {
			final float[] source = Arrays.copyOf(data, data.length);

			for (int i = kSize; i < width - kSize; i++) {
				for (int j = kSize; j < height - kSize; j++) {

					for (int k = 0; k < kernel.length; k++) {
						for (int m = 0; m < kernel.length; m++) {

							final float deltaChange = source[getOffset(i + k - kSize, j + m - kSize)] * kernel[k][m];

							writeLock.lock();
							data[getOffset(i, j)] += deltaChange;
							writeLock.unlock();

							if (Math.abs(deltaChange) > Math.abs(maxDelta)) {
								maxDelta = deltaChange;
							}
						}
					}
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void setValue(float[] area, int areaWidth, int areaHeight, int offsetX, int offsetY) {

	}

	public void setBorderValue(Border border, float value) {
		writeLock.lock();
		try {
			switch (border) {
			case NORTH:
				Arrays.fill(data, 0, width + 2, value);
				break;
			case EAST:

				break;
			case SOUTH:
				Arrays.fill(data, getOffset(0, height), width + 2, value);
				break;
			case WEST:

				break;
			}
		} finally {
			writeLock.unlock();
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
