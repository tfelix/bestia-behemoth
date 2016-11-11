package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.shape.Size;

public class TilesetTest {

	@Test
	public void contains_containingInteger_true() {
		Tileset ts = getTs();
		Assert.assertTrue(ts.contains(156));
	}

	@Test
	public void contains_nonContainingInteger_false() {
		Tileset ts = getTs();
		Assert.assertFalse(ts.contains(1345));
	}

	@Test
	public void contains_containingTile_true() {
		Tileset ts = getTs();
		Tile t = new Tile(2);
		Assert.assertTrue(ts.contains(t));
	}

	@Test
	public void contains_nonContainingTile_false() {
		Tileset ts = getTs();
		Tile t = new Tile(2);
		Assert.assertFalse(ts.contains(t));
	}

	@Test
	public void getFirstGid_correct() {
		Tileset ts = getTs();
		Assert.assertEquals(100, ts.getFirstGID());
	}

	@Test
	public void getName_correct() {
		Tileset ts = getTs();
		Assert.assertEquals("test", ts.getName());
	}

	@Test
	public void setProperties_correct_ok() {
		Tileset ts = getTs();
		int gid = 123;
		TileProperties props = new TileProperties(false, 100);
		ts.setProperties(gid, props);
		Assert.assertEquals(props, ts.getProperties(gid));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void setProperties_nonExistingId_throws() {
		Tileset ts = getTs();
		ts.setProperties(1000, new TileProperties(false, 100));
	}
	
	private Tileset getTs() {
		return new Tileset("test", new Size(10,10), 100);
	}

}
