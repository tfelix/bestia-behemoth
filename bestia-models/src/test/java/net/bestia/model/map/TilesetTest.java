package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.geometry.Size;
import net.bestia.model.map.TileProperties;
import net.bestia.model.map.Tileset;


public class TilesetTest {

	@Test(expected=IllegalArgumentException.class)
	public void setProperties_invalidGid_throws() {
		Tileset set = getTileset();
		set.setProperties(1234, new TileProperties(true, 100, false));
	}

	@Test
	public void setProperties_validGid_ok() {
		Tileset set = getTileset();
		set.setProperties(145, new TileProperties(true, 100, false));
		set.setProperties(100, new TileProperties(true, 100, false));
		set.setProperties(200, new TileProperties(true, 100, false));
	}

	@Test(expected=NullPointerException.class)
	public void setProperties_nullProperties_throws() {
		Tileset set = getTileset();
		set.setProperties(145, null);
	}
	
	@Test
	public void contains_validGid_true() {
		Tileset set = getTileset();
		Assert.assertTrue(set.contains(100));
		Assert.assertTrue(set.contains(156));
		Assert.assertTrue(set.contains(200));
	}
	
	@Test
	public void contains_invalidGid_false() {
		Tileset set = getTileset();
		Assert.assertFalse(set.contains(1));
		Assert.assertFalse(set.contains(99));
		Assert.assertFalse(set.contains(201));
	}

	public Tileset getTileset() {
		return new Tileset("test", new Size(320, 320), 100);
	}
}
