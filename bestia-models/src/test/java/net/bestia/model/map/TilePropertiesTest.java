package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

public class TilePropertiesTest {
	
	@Test(expected=IllegalArgumentException.class)
	public void negWalkspeedSpeed_throws() {
		new TileProperties(true, -100);
	}
	
	@Test
	public void correctArgs_getterOk() {
		TileProperties p = new TileProperties(true, 100);
		Assert.assertTrue(p.isWalkable());
		Assert.assertEquals(100, p.getWalkspeed());
	}

}
