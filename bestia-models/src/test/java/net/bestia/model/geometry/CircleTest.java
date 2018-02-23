package net.bestia.model.geometry;

import org.junit.Assert;
import org.junit.Test;

public class CircleTest {

	@Test(expected = IllegalArgumentException.class)
	public void negRadius_throws() {
		new Circle(10, 10, -4);
	}

	@Test
	public void ctor_ok() {
		new Circle(3, 10, 2);
	}

	@Test
	public void getCenter_ok() {
		Circle c = new Circle(3, 10, 2);
		Assert.assertTrue(c.getCenter().equals(new Point(3, 10)));
	}

	@Test
	public void getAnchor_ok() {
		Circle c = new Circle(3, 10, 2);
		Assert.assertTrue(c.getAnchor().equals(new Point(3, 10)));

		c = new Circle(10, 10, 5, 12, 12);
		Assert.assertTrue(c.getAnchor().equals(new Point(12, 12)));
	}

	@Test
	public void getBoundingBox_ok() {
		Circle c = new Circle(3, 10, 2);
		Rect bb = c.getBoundingBox();
		Assert.assertEquals(new Rect(1, 8, 4, 4), bb);
	}

	@Test
	public void collide_circle_ok() {
		Circle c = new Circle(10, 10, 2);
		Circle c2 = new Circle(15, 15, 2);
		Circle c3 = new Circle(10, 11, 5);
		
		Assert.assertTrue(c.collide(c3));
		Assert.assertFalse(c.collide(c2));
		Assert.assertTrue(c2.collide(c3));
	}

	@Test
	public void collide_point_ok() {
		Circle c = new Circle(10, 10, 2);
		Point p1 = new Point(12, 10);
		Point p2 = new Point(10, 10);
		Point p3 = new Point(45, 23);
		
		Assert.assertTrue(c.collide(p1));
		Assert.assertTrue(c.collide(p2));
		Assert.assertFalse(c.collide(p3));
	}

	@Test
	public void collide_rect_ok() {
		Circle c = new Circle(10, 10, 2);
		Rect r1 = new Rect(3, 3, 20, 20);
		Rect r2 = new Rect(12, 10, 4, 4);
		Rect r3 = new Rect(10, 13, 5, 5);
		
		Assert.assertTrue(c.collide(r1));
		Assert.assertTrue(c.collide(r2));
		Assert.assertFalse(c.collide(r3));
	}

	@Test
	public void moveByAnchor_ok() {
		Circle c1 = new Circle(10, 10, 2);
		Circle c2 = new Circle(14, 14, 4, 16, 16);
		
		c1 = c1.moveByAnchor(15, 16);
		c2 = c2.moveByAnchor(10, 10);
		
		Assert.assertTrue(c1.getCenter().equals(new Point(15, 16)));
		Assert.assertTrue(c2.getCenter().equals(new Point(8, 8)));
	}

}
