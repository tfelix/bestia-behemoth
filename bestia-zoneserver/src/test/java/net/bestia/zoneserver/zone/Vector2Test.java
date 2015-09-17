package net.bestia.zoneserver.zone;

import org.junit.Assert;
import org.junit.Test;

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
	
}
