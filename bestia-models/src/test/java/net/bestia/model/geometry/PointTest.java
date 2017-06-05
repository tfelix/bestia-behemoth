package net.bestia.model.geometry;

import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.geometry.Circle;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;

public class PointTest {
	
	@Test
	public void is_serializable() {
		Assert.assertTrue(Serializable.class.isAssignableFrom(Point.class));
	}

	@Test
	public void moveAnchor() {
		Point p = new Point(10, 10);
		p = (Point) p.moveByAnchor(3, 3);
		Assert.assertEquals(3, p.getX());
		Assert.assertEquals(3, p.getY());
	}

	@Test
	public void collide_point_success() {
		Point p1 = new Point(10, 10);
		Point p2 = new Point(10, 10);
		
		Assert.assertTrue(p1.collide(p2));
	}

	@Test
	public void collide_point_fail() {
		Point p1 = new Point(10, 10);
		Point p2 = new Point(11, 10);
		
		Assert.assertFalse(p1.collide(p2));
	}

	@Test
	public void collide_circle_success() {
		Point p1 = new Point(10, 10);
		Circle c = new Circle(13, 10, 4);
		
		Assert.assertTrue(p1.collide(c));
	}

	@Test
	public void collide_circle_fail() {
		Point p1 = new Point(10, 10);
		Circle c = new Circle(13, 10, 3);
		
		Assert.assertTrue(p1.collide(c));
	}

	@Test
	public void collide_rect_success() {
		Point p1 = new Point(10, 10);
		Rect r = new Rect(9, 9, 5, 5);
		
		Assert.assertTrue(p1.collide(r));
	}

	@Test
	public void collide_rect_fail() {
		Point p1 = new Point(10, 10);
		Rect r = new Rect(11, 10, 5, 5);
		
		Assert.assertFalse(p1.collide(r));
	}

	@Test
	public void getAnchor() {
		Point p1 = new Point(10, 10);
		Assert.assertEquals(p1, p1.getAnchor());
	}
	
	@Test
	public void getBoundingBox() {
		Point p1 = new Point(10, 10);
		Assert.assertEquals(10, p1.getBoundingBox().getX());
		Assert.assertEquals(10, p1.getBoundingBox().getY());
		Assert.assertEquals(1, p1.getBoundingBox().getHeight());
		Assert.assertEquals(1, p1.getBoundingBox().getWidth());
	}
	
	
	public void getDistance() {
		// TODO
	}
	
	public void jackson_serialize() {
		// TODO
	}

}
