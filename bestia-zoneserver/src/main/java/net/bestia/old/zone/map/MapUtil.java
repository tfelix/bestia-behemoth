package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.Point;

public final class MapUtil {

	/**
	 * Calculates the euclidian distance between to vectors.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static int getDistance(Point p1, Point p2) {
		final int dX = p1.x - p2.x;
		final int dY = p1.y - p2.y;
		
		return (int) Math.sqrt(dX * dX + dY * dY);
	}

}
