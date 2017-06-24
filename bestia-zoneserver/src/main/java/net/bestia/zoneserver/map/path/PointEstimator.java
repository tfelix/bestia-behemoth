package net.bestia.zoneserver.map.path;

import net.bestia.model.geometry.Point;

/**
 * Heuristically estimates the distances of {@link Point}s.
 * 
 * @author Thomas Felix
 *
 */
public class PointEstimator implements HeuristicEstimator<Point> {

	@Override
	public float getDistance(Point current, Point target) {
		return (float) current.getDistance(target);
	}

}
