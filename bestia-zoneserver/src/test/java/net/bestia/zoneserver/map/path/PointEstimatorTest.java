package net.bestia.zoneserver.map.path;

import org.junit.Assert;
import org.junit.Test;

import bestia.model.geometry.Point;

public class PointEstimatorTest {
	
	@Test
	public void getDistance_twoPoints_euclidianDistance() {
		
		PointEstimator pe = new PointEstimator();
		
		Point p1 = new Point(1, 5);
		Point p2 = new Point(3, 10);
		
		float d = pe.getDistance(p1, p2);
		Assert.assertEquals(5.385, d, 0.01);
	}

}
