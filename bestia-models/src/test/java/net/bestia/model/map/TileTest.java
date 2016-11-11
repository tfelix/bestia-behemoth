package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

public class TileTest {

	@Test(expected = NullPointerException.class)
	public void ctor_nullPoint_throws() {
		new Tile(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_negLayer_throws() {
		new Tile(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_negGid_throws() {
		new Tile(1);
	}

	@Test
	public void ctor_correct_setterOk() {
		Tile t = new Tile(120);
		Assert.assertEquals(120, t.getGid());
	}
}
