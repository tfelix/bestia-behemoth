package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.geometry.Point;

public class MapChunkTest {

	private int[] groundLayer = new int[MapChunk.MAP_CHUNK_SIZE_AREA];
	private int[] groundLayerBig = new int[MapChunk.MAP_CHUNK_SIZE_AREA + 10];

	public MapChunkTest() {
		
		
		for (int i = 0; i < groundLayer.length; i++) {
			groundLayer[i] = 10;
		}

		for (int i = 0; i < groundLayerBig.length; i++) {
			groundLayerBig[i] = 10;
		}
	}

	@Test
	public void ctor1_ok() {
		new MapChunk(new Point(5, 4), groundLayer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor1_layerTooBig_throws() {
		new MapChunk(new Point(5, 4), groundLayerBig);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor1_negPoint_throws() {
		new MapChunk(new Point(-5, 4), groundLayer);
	}

	@Test
	public void ctor2_ok() {

	}

	@Test
	public void ctor3_ok() {

	}

	@Test
	public void ctor2_nok_throws() {

	}

	@Test
	public void ctor3_ok_throws() {

	}

	@Test
	public void getGid_outOfRange_minusOne() {
		MapChunk mc = new MapChunk(new Point(5, 4), groundLayer);
		Assert.assertEquals(-1, mc.getGid(new Point(-10, 3)));
	}

	@Test
	public void getGid_outOfLayers_minusOne() {
		MapChunk mc = new MapChunk(new Point(5, 4), groundLayer);
		Assert.assertEquals(-1, mc.getGid(10, new Point(-10, 3)));
		Assert.assertEquals(-1, mc.getGid(-3, new Point(-10, 3)));
	}

	@Test
	public void getGid_ok() {
		MapChunk mc = new MapChunk(new Point(5, 4), groundLayer);
		Assert.assertEquals(10, mc.getGid(new Point(1, 1)));
		Assert.assertEquals(10, mc.getGid(1, new Point(1, 1)));
		// Ground
		Assert.assertEquals(10, mc.getGid(0, new Point(1, 1)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void getChunkCords_negative_throws() {
		MapChunk.getChunkCords(new Point(-1, 5));
	}

	@Test
	public void getChunkCords_ok() {
		Point p = MapChunk.getChunkCords(new Point(15, 5));
		Assert.assertEquals(new Point(1, 0), p);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getWorldCords_negative_throws() {
		MapChunk.getWorldCords(new Point(-1, 0));
	}

	@Test
	public void getWorldCords_ok() {
		Point p = MapChunk.getWorldCords(new Point(5, 5));
		Assert.assertEquals(new Point(50, 50), p);
	}

	public void getWorldRect_negative_throws() {

	}

	public void getWorldRect_ok() {

	}
}
