package net.bestia.zoneserver.zone;

import org.junit.Assert;
import org.junit.Test;

public class RectTest {

	@Test
	public void ctor_legal_works() {
		new Rect(3, 5, 10, 10);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_illegal_throws() {
		new Rect(-3, 5, 10, 10);
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
}
