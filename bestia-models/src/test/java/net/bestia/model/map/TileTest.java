package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.shape.Point;

public class TileTest {

	@Test(expected = NullPointerException.class)
	public void ctor_nullPoint_throws() {
		new Tile(1, null, 120);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_negLayer_throws() {
		new Tile(1, new Point(21, 20), 120);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_negGid_throws() {
		new Tile(1, new Point(1, 1), -120);
	}

	@Test
	public void ctor_correct_setterOk() {
		Tile t = getTile();
		Assert.assertEquals(0, t.getLayer());
		Assert.assertEquals(new Point(10, 10), t.getPoint());
		Assert.assertEquals(120, t.getGid());
	}

	private Tile getTile() {
		return new Tile(0, new Point(10, 10), 120);
	}
}
