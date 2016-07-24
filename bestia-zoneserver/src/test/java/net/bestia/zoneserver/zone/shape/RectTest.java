package net.bestia.zoneserver.zone.shape;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.zone.shape.Rect;

public class RectTest {

	@Test
	public void ctor_legal_works() {
		new Rect(3, 5, 10, 10);
		new Rect(-3, 5, 10, 10);
		new Rect(3, -6, 10, 10);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_illegaldimension_throws() {
		new Rect(5, 5, -10, 5);
	}

	@Test
	public void hash_same_true() {
		Rect r1 = new Rect(7, 7, 5, 5);
		Rect r2 = new Rect(7, 7, 5, 5);
		Assert.assertEquals(r1.hashCode(), r2.hashCode());
	}

	@Test
	public void hash_different_false() {
		Rect r1 = new Rect(7, 7, 5, 5);
		Rect r2 = new Rect(7, 7, 5, 5);
		Assert.assertEquals(r1.hashCode(), r2.hashCode());
	}

	@Test
	public void equals_same_true() {
		Rect r1 = new Rect(7, 7, 5, 5);
		Assert.assertTrue(r1.equals(r1));
		Rect r2 = new Rect(7, 7, 5, 5);
		Assert.assertTrue(r1.equals(r2));
	}

	@Test
	public void equals_differentdimension_false() {
		Rect r1 = new Rect(7, 7, 4, 8);
		Rect r2 = new Rect(7, 7, 5, 5);
		Assert.assertFalse(r1.equals(r2));
	}
	
	@Test
	public void equals_differentxy_false() {
		Rect r1 = new Rect(7, 7, 4, 8);
		Rect r2 = new Rect(7, 7, 5, 5);
		Assert.assertFalse(r1.equals(r2));
	}
	
	@Test
	public void collide_rect_true() {
		Rect r1 = new Rect(5, 5, 3, 3);
		Rect r2 = new Rect(5, 7, 3, 3);
		
		Assert.assertTrue(r1.collide(r2));
		Assert.assertTrue(r2.collide(r1));
	}
	
	@Test
	public void collide_rect_false() {
		Rect r1 = new Rect(5, 5, 3, 3);
		Rect r2 = new Rect(5, 9, 3, 3);
		Assert.assertFalse(r1.collide(r2));
		Assert.assertFalse(r2.collide(r1));
	}

	@Test
	public void collide_vector2_true() {
		Rect r1 = new Rect(5, 5, 3, 3);
		Point v1 = new Point(7, 8);
		Assert.assertTrue(r1.collide(v1));
	}
	
	@Test
	public void collide_vector2_false() {
		Rect r1 = new Rect(5, 5, 5, 3);
		Point v1 = new Point(17, 9);
		Assert.assertFalse(r1.collide(v1));
	}

	@Test
	public void get_bounding_box() {
		Rect d = new Rect(10, 10, 20, 20);
		Rect bb = d.getBoundingBox();

		Assert.assertEquals(bb, d);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void ctor_negativeAnchor_exception() {
		new Rect(10, 10, 4, 4, 5, 12);
	}
	
	@Test
	public void getAnchor_std_middle() {
		Rect r = new Rect(10, 10, 4, 4);
		Point a = r.getAnchor();
		Assert.assertEquals(12, a.x);
		Assert.assertEquals(12, a.y);
	}
	
	public void getAnchor_setAnchor_bottomRight() {
		Rect r = new Rect(10, 10, 4, 4, 14, 14);
		Point a = r.getAnchor();
		Assert.assertEquals(14, a.x);
		Assert.assertEquals(14, a.y);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void ctor_biggerOneAnchor_exception() {
		new Rect(10, 10, 4, 4, 12, 18);
	}
	
	@Test
	public void moveByAnchor() {
		// Anchor ist bottom middle.
		Rect r = new Rect(4, 4, 4, 4, 6, 8);
		// Move 2 to the left.
		Rect r2 = (Rect) r.moveByAnchor(4, 8);
		
		Assert.assertEquals(2, r2.getX());
		Assert.assertEquals(4, r2.getY());
		
		Assert.assertEquals(4, r2.getWidth());
		Assert.assertEquals(4, r2.getHeight());
		
		Assert.assertEquals(4, r2.getAnchor().x);
		Assert.assertEquals(8, r2.getAnchor().y);
	}
}
