package net.bestia.model.geometry;

import org.junit.Assert;
import org.junit.Test;

public class RectTest {
	
	@Test
	public void moveAnchor() {
		Rect r = new Rect(10, 10 , 3, 3);
		r = (Rect) r.moveByAnchor(15, 15);
		
		Assert.assertEquals(14, r.getX());
		Assert.assertEquals(14, r.getY());
		Assert.assertEquals(15, r.getAnchor().getX());
		Assert.assertEquals(15, r.getAnchor().getY());
	}
	
	@Test
	public void ctor_anchorAtCorners() {
		new Rect(0, 0, 20, 20);
		new Rect(-10, -10, 30, 30);
		new Rect(-10, -10, 30, 30, -10, -10);
	}

	@Test
	public void collide_point_success() {
		Rect r1 = new Rect(10, 10, 15, 15);
		Point p2 = new Point(10, 10);
		
		Assert.assertTrue(r1.collide(p2));
		Assert.assertTrue(p2.collide(r1));
	}

	@Test
	public void collide_point_fail() {
		Rect r1 = new Rect(10, 10, 15, 15);
		Point p2 = new Point(9, 10);
		Point p3 = new Point(25, 28);
		
		Assert.assertFalse(r1.collide(p2));
		Assert.assertFalse(r1.collide(p3));
		Assert.assertFalse(p2.collide(r1));
		Assert.assertFalse(p3.collide(r1));
	}

	@Test
	public void collide_circle_success() {
		Rect r = new Rect(10, 10, 5, 5);
		Circle c = new Circle(18, 10, 7);
		
		Assert.assertTrue(r.collide(c));
		Assert.assertTrue(c.collide(r));
	}

	@Test
	public void collide_circle_fail() {
		Rect r = new Rect(10, 10, 5, 5);
		Circle c = new Circle(18, 10, 2);
		
		Assert.assertFalse(r.collide(c));
		Assert.assertFalse(c.collide(r));
	}

	@Test
	public void collide_rect_success() {
		Rect r = new Rect(10, 10, 10, 10);
		Rect r2 = new Rect(11, 10, 5, 5);
		
		Assert.assertTrue(r.collide(r2));
		Assert.assertTrue(r2.collide(r));
	}

	@Test
	public void collide_rect_fail() {
		Rect r = new Rect(10, 10);
		Rect r2 = new Rect(11, 10, 5, 5);
		
		Assert.assertFalse(r.collide(r2));
		Assert.assertFalse(r2.collide(r));
	}

	@Test
	public void getAnchor_anchorInMiddle() {
		Rect r = new Rect(12, 12, 3, 3);
		Assert.assertEquals(new Point(13, 13), r.getAnchor());
	}
	
	@Test
	public void getBoundingBox() {
		Rect r = new Rect(10, 10, 5, 5);
		Assert.assertEquals(10, r.getBoundingBox().getX());
		Assert.assertEquals(10, r.getBoundingBox().getY());
		Assert.assertEquals(5, r.getBoundingBox().getHeight());
		Assert.assertEquals(5, r.getBoundingBox().getWidth());
	}
}
