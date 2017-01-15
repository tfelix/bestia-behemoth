package net.bestia.model.geometry;

import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

public class SizeTest {
	
	@Test
	public void is_serializable() {
		Assert.assertTrue(Serializable.class.isAssignableFrom(Size.class));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_negativeValue_throws() {
		new Size(0, -10);
	}
	
	@Test
	public void getter_ctor() {
		Size s = new Size(123, 10);
		Assert.assertEquals(123, s.getWidth());
		Assert.assertEquals(10, s.getHeight());
	}
}
