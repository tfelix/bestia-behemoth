package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.Point;

public final class MapUtil {
	
	private MapUtil() {
		// no op.
	}

	/**
	 * Calculates the euclidian distance between to vectors.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static long getDistance(Point p1, Point p2) {
		final long dX = p1.x - p2.x;
		final long dY = p1.y - p2.y;
		
		return (long) Math.sqrt(dX * dX + dY * dY);
	}

}
