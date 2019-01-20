package net.bestia.model.map

import java.util.ArrayList
import java.util.HashMap

import org.junit.Assert
import org.junit.Test

import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect

class MapChunkTest {

  private val groundLayer = IntArray(MapChunk.MAP_CHUNK_SIZE_AREA)
  private val groundLayerBig = IntArray(MapChunk.MAP_CHUNK_SIZE_AREA + 10)

  init {

    for (i in groundLayer.indices) {
      groundLayer[i] = 10
    }

    for (i in groundLayerBig.indices) {
      groundLayerBig[i] = 10
    }
  }

  @Test
  fun ctor1_ok() {
    MapChunk(Point(5, 4), groundLayer)
  }

  @Test(expected = IllegalArgumentException::class)
  fun ctor1_layerTooBig_throws() {
    MapChunk(Point(5, 4), groundLayerBig)
  }

  @Test(expected = IllegalArgumentException::class)
  fun ctor1_negPoint_throws() {
    MapChunk(Point(-5, 4), groundLayer)
  }

  @Test
  fun getGid_outOfRange_minusOne() {
    val mc = MapChunk(Point(5, 4), groundLayer)
    Assert.assertEquals(-1, mc.getGid(Point(-10, 3)).toLong())
  }

  @Test
  fun getGid_outOfLayers_minusOne() {
    val mc = MapChunk(Point(5, 4), groundLayer)
    Assert.assertEquals(-1, mc.getGid(10, Point(-10, 3)).toLong())
    Assert.assertEquals(-1, mc.getGid(-3, Point(-10, 3)).toLong())
  }

  @Test
  fun getGid_ok() {
    val layers = ArrayList<Map<Point, Int>>()
    val layer1 = HashMap<Point, Int>()
    layers.add(layer1)
    layer1[Point(2, 3)] = 10
    layer1[Point(6, 2)] = 11

    val mc = MapChunk(Point(5, 4), groundLayer, layers)

    Assert.assertEquals(10, mc.getGid(Point(1, 1)).toLong())
    Assert.assertEquals(-1, mc.getGid(1, Point(1, 1)).toLong())
    Assert.assertEquals(10, mc.getGid(1, Point(2, 3)).toLong())
    Assert.assertEquals(11, mc.getGid(1, Point(6, 2)).toLong())
    // Ground
    Assert.assertEquals(10, mc.getGid(0, Point(1, 1)).toLong())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getChunkCords_negative_throws() {
    MapChunk.getChunkCords(Point(-1, 5))
  }

  @Test
  fun getChunkCords_ok() {
    val p = MapChunk.getChunkCords(Point(105, 5))
    Assert.assertEquals(Point(5, 5), p)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getWorldCords_negative_throws() {
    MapChunk.getWorldCords(Point(-1, 0))
  }

  @Test
  fun getWorldCords_ok() {
    val p = MapChunk.getWorldCords(Point(5, 5))
    Assert.assertEquals(Point(50, 50), p)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getWorldRect_negative_throws() {
    MapChunk.getWorldRect(Point(-1, 5))
  }

  @Test
  fun getWorldRect_ok() {
    val r = MapChunk.getWorldRect(Point(1, 5))
    Assert.assertEquals(Rect(10, 50, 10, 10), r)
  }
}
