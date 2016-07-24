package net.bestia.zoneserver.zone.shape;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.zone.shape.Point;

public class Vector2Test {

	@Test
	public void ctor_normal_works() {
		new Point(10, 5);
		new Point(-10, 5);
	}

	@Test
	public void hash_same_equal() {
		Point v1 = new Point(5, 5);
		Point v2 = new Point(5, 5);

		Assert.assertEquals(v1.hashCode(), v2.hashCode());
	}

	@Test
	public void hash_different_notequal() {
		Point v1 = new Point(3, 7);
		Point v2 = new Point(8, 15);

		Assert.assertNotEquals(v1.hashCode(), v2.hashCode());
	}

	@Test
	public void equals_same_true() {
		Point v1 = new Point(5, 6);
		Assert.assertTrue(v1.equals(v1));
		Point v2 = new Point(5, 6);
		Assert.assertTrue(v1.equals(v2));
	}

	@Test
	public void equals_different_false() {
		Point v1 = new Point(5, 6);
		Point v2 = new Point(8, 3);
		Assert.assertFalse(v1.equals(v2));
	}

	@Test
	public void collides_vector_true() {
		Point v1 = new Point(5, 6);
		Point v2 = new Point(5, 6);
		Assert.assertTrue(v1.collide(v2));
		Assert.assertTrue(v2.collide(v1));
	}

	@Test
	public void collides_vector_false() {
		Point v1 = new Point(5, 6);
		Point v2 = new Point(7, 6);
		Assert.assertFalse(v1.collide(v2));
		Assert.assertFalse(v2.collide(v1));
	}

	@Test
	public void collides_circle_false() {
		Point v1 = new Point(3, 6);
		Circle c = new Circle(10, 10, 3);
		Assert.assertFalse(v1.collide(c));
	}

	@Test
	public void collides_circle_true() {
		Point v1 = new Point(3, 6);
		Circle c = new Circle(5, 5, 3);
		Assert.assertTrue(v1.collide(c));
	}

	@Test
	public void collides_rect_false() {
		Point v1 = new Point(3, 6);
		Rect r = new Rect(9, 23, 5, 5);
		Assert.assertFalse(v1.collide(r));
	}

	@Test
	public void collides_rect_true() {
		Point v1 = new Point(8, 6);
		Rect r = new Rect(5, 5, 10, 50);
		Assert.assertTrue(v1.collide(r));
	}
	
	@Test
	public void getboundingbox() {
		Point v1 = new Point(3, 6);
		Rect r = v1.getBoundingBox();
		
		Assert.assertEquals(0, r.getHeight());
		Assert.assertEquals(0, r.getWidth());
		Assert.assertEquals(3, r.getX());
		Assert.assertEquals(6, r.getY());
	}
	
	@Test
	public void getAnchor_sameAsXY() {
		Point v = new Point(10, 12);
		Assert.assertEquals(10, v.getAnchor().x);
		Assert.assertEquals(12, v.getAnchor().y);
	}
	
	@Test
	public void moveByAnchor() {
		Point v = new Point(10, 10);
		Point v2 = (Point) v.moveByAnchor(13, 15);
		Assert.assertEquals(13, v2.x);
		Assert.assertEquals(15, v2.y);
	}
}
