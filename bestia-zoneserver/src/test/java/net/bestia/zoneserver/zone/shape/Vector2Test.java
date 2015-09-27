package net.bestia.zoneserver.zone.shape;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.zone.shape.Vector2;

public class Vector2Test {

	@Test
	public void ctor_normal_works() {
		new Vector2(10, 5);
		new Vector2(-10, 5);
	}

	@Test
	public void hash_same_equal() {
		Vector2 v1 = new Vector2(5, 5);
		Vector2 v2 = new Vector2(5, 5);

		Assert.assertEquals(v1.hashCode(), v2.hashCode());
	}

	@Test
	public void hash_different_notequal() {
		Vector2 v1 = new Vector2(3, 7);
		Vector2 v2 = new Vector2(8, 15);

		Assert.assertNotEquals(v1.hashCode(), v2.hashCode());
	}

	@Test
	public void equals_same_true() {
		Vector2 v1 = new Vector2(5, 6);
		Assert.assertTrue(v1.equals(v1));
		Vector2 v2 = new Vector2(5, 6);
		Assert.assertTrue(v1.equals(v2));
	}

	@Test
	public void equals_different_false() {
		Vector2 v1 = new Vector2(5, 6);
		Vector2 v2 = new Vector2(8, 3);
		Assert.assertFalse(v1.equals(v2));
	}

	@Test
	public void collides_vector_true() {
		Vector2 v1 = new Vector2(5, 6);
		Vector2 v2 = new Vector2(5, 6);
		Assert.assertTrue(v1.collide(v2));
		Assert.assertTrue(v2.collide(v1));
	}

	@Test
	public void collides_vector_false() {
		Vector2 v1 = new Vector2(5, 6);
		Vector2 v2 = new Vector2(7, 6);
		Assert.assertFalse(v1.collide(v2));
		Assert.assertFalse(v2.collide(v1));
	}

	@Test
	public void collides_circle_false() {
		Vector2 v1 = new Vector2(3, 6);
		Circle c = new Circle(10, 10, 3);
		Assert.assertFalse(v1.collide(c));
	}

	@Test
	public void collides_circle_true() {
		Vector2 v1 = new Vector2(3, 6);
		Circle c = new Circle(5, 5, 3);
		Assert.assertTrue(v1.collide(c));
	}

	@Test
	public void collides_rect_false() {
		Vector2 v1 = new Vector2(3, 6);
		Rect r = new Rect(9, 23, 5, 5);
		Assert.assertFalse(v1.collide(r));
	}

	@Test
	public void collides_rect_true() {
		Vector2 v1 = new Vector2(8, 6);
		Rect r = new Rect(5, 5, 10, 50);
		Assert.assertTrue(v1.collide(r));
	}
	
	@Test
	public void getboundingbox() {
		Vector2 v1 = new Vector2(3, 6);
		Rect r = v1.getBoundingBox();
		
		Assert.assertEquals(0, r.getHeight());
		Assert.assertEquals(0, r.getWidth());
		Assert.assertEquals(3, r.getX());
		Assert.assertEquals(6, r.getY());
	}
}
