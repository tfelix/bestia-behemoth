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
		Vector2 v = new Vector2(10, 14);
		
		Assert.assertFalse(c1.collide(v));
	}
	
	@Test
	public void collide_vector2_true() {
		Circle c1 = new Circle(5, 5, 3);
		Vector2 v = new Vector2(7, 6);
		
		Assert.assertTrue(c1.collide(v));
	}
	
	@Test
	public void collide_rect_true() {
		Circle c1 = new Circle(5, 5, 3);
		Rect r1 = new Rect(4, 4, 10, 10);		
		Rect r2 = new Rect(8, 8, 5, 5);
		
		Assert.assertTrue(c1.collide(r1));
		Assert.assertTrue(c1.collide(r2));
	}
	
	@Test
	public void collide_rect_false() {
		Circle c1 = new Circle(5, 5, 3);
		Rect r1 = new Rect(10, 10, 3, 3);	
		
		Assert.assertFalse(c1.collide(r1));
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
}
