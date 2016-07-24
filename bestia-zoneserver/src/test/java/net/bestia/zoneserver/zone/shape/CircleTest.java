package net.bestia.zoneserver.zone.shape;

import org.junit.Assert;
import org.junit.Test;

public class CircleTest {

	@Test(expected = IllegalArgumentException.class)
	public void ctor_negRadius_execption() {
		new Circle(5, 5, -3);
	}

	@Test
	public void ctor_negXY_nothing() {
		Circle c = new Circle(-7, -4, 3);
		Assert.assertNotNull(c);
	}

	@Test
	public void equals_same_true() {
		Circle c1 = new Circle(5, 5, 3);
		Circle c2 = new Circle(5, 5, 3);

		Assert.assertTrue(c1.equals(c2));
	}

	@Test
	public void equals_notSame_false() {
		Circle c1 = new Circle(5, 5, 3);
		Circle c2 = new Circle(5, 5, 7);

		Assert.assertFalse(c1.equals(c2));
	}

	@Test
	public void hash_same_equal() {
		Circle c1 = new Circle(5, 5, 7);
		Circle c2 = new Circle(5, 5, 7);

		Assert.assertEquals(c1.hashCode(), c2.hashCode());
	}

	@Test
	public void hash_notSame_equal() {
		Circle c1 = new Circle(5, 5, 7);
		Circle c2 = new Circle(5, 8, 7);

		Assert.assertNotEquals(c1.hashCode(), c2.hashCode());
	}

	@Test
	public void collide_circle_true() {
		Circle c1 = new Circle(5, 5, 7);
		Circle c2 = new Circle(5, 8, 7);

		Assert.assertTrue(c1.collide(c2));
		Assert.assertTrue(c2.collide(c1));
	}

	@Test
	public void collide_circle_false() {
		Circle c1 = new Circle(5, 5, 7);
		Circle c2 = new Circle(5, 10, 2);

		Assert.assertNotEquals(c1.hashCode(), c2.hashCode());
	}

	@Test
	public void collide_vector2_false() {
		Circle c1 = new Circle(5, 5, 3);
		Point v = new Point(10, 14);

		Assert.assertFalse(c1.collide(v));
	}

	@Test
	public void collide_vector2_true() {
		Circle c1 = new Circle(5, 5, 3);
		Point v = new Point(7, 6);

		Assert.assertTrue(c1.collide(v));
	}

	@Test
	public void collide_rect_true() {
		Circle c1 = new Circle(6, 6, 2);
		Rect r1 = new Rect(1, 1, 20, 20);
		Rect r2 = new Rect(7, 2, 5, 5);

		Assert.assertTrue(c1.collide(r1));
		Assert.assertTrue(c1.collide(r2));
	}

	@Test
	public void collide_rect_false() {
		// Top circle.
		Circle c1 = new Circle(12, 5, 2);
		// Top right circle.
		Circle c2 = new Circle(20, 5, 2);
		// Right circle
		Circle c3 = new Circle(20, 11, 2);
		// Right bottom circle.
		Circle c4 = new Circle(18, 18, 2);
		// Bottom circle.
		Circle c5 = new Circle(12, 18, 2);
		// Bottom left circle.
		Circle c6 = new Circle(5, 18, 2);
		// Left circle.
		Circle c7 = new Circle(4, 12, 2);
		Rect r1 = new Rect(10, 10, 5, 5);

		Assert.assertFalse(c1.collide(r1));
		Assert.assertFalse(c2.collide(r1));
		Assert.assertFalse(c3.collide(r1));
		Assert.assertFalse(c4.collide(r1));
		Assert.assertFalse(c5.collide(r1));
		Assert.assertFalse(c6.collide(r1));
		Assert.assertFalse(c7.collide(r1));
	}

	@Test
	public void getboundingbox() {
		Circle c = new Circle(5, 5, 2);
		Rect r = c.getBoundingBox();

		Assert.assertEquals(3, r.getX());
		Assert.assertEquals(3, r.getY());
		Assert.assertEquals(4, r.getWidth());
		Assert.assertEquals(4, r.getHeight());
	}

	@Test
	public void getanchor_iscenter() {
		Circle c = new Circle(5, 5, 3);
		Point a = c.getAnchor();

		Assert.assertEquals(5, a.x);
		Assert.assertEquals(5, a.y);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getAnchor_leftFromCircle_exception() {
		new Circle(5, 5, 2, 2, 2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_anchoroutside_exception() {
		new Circle(5, 5, 2, 6, 8);
	}
	
	@Test
	public void ctor_inside_ok() {
		new Circle(5, 5, 2, 7, 7);
	}
}
