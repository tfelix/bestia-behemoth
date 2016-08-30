package net.bestia.model.zone;

import java.io.Serializable;

/**
 * Immutable point object.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Point implements Serializable {

	private static final long serialVersionUID = 1L;
	private final long x;
	private final long y;

	public Point(long x, long y) {

		this.x = x;
		this.y = y;
	}

	public long getX() {
		return x;
	}
	
	public long getY() {
		return y;
	}
	
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (x ^ (x >>> 32));
		result = prime * result + (int) (y ^ (y >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("Point[x: %d, y: %d]", x, y);
	}
}
