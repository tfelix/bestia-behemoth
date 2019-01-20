package net.bestia.model.map

import net.bestia.model.geometry.Rect
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

import java.util.Arrays
import java.util.HashSet

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat

@RunWith(MockitoJUnitRunner::class)
class MapDataDTOTest {


  @Test(expected = IllegalArgumentException::class)
  fun join_nonAdjacentDTO_throws() {

    val r1 = Rect(10, 10, 10, 10)
    val r2 = Rect(23, 10, 10, 10)

    val md1 = MapDataDTO(r1)
    val md2 = MapDataDTO(r2)

    md1.join(md2)
  }

  @Test
  fun getGroundGid_borderCoordinates_ok() {

    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)
    fillGid(md1, 1)

    assertEquals(1, md1.getGroundGid(10, 20).toLong())
    assertEquals(1, md1.getGroundGid(20, 20).toLong())
    assertEquals(1, md1.getGroundGid(10, 30).toLong())
    assertEquals(1, md1.getGroundGid(20, 30).toLong())
    assertEquals(1, md1.getGroundGid(15, 25).toLong())
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun getGroundGid_outOfBoundsCords_throws() {

    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)
    fillGid(md1, 1)

    assertEquals(1, md1.getGroundGid(21, 23).toLong())
  }

  @Test
  fun putGroundLayer_borderCoordinates_ok() {

    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)

    md1.putGroundLayer(10, 20, 1)
    md1.putGroundLayer(20, 20, 2)
    md1.putGroundLayer(10, 30, 3)
    md1.putGroundLayer(20, 30, 4)
    md1.putGroundLayer(15, 25, 5)

    assertEquals(1, md1.getGroundGid(10, 20).toLong())
    assertEquals(2, md1.getGroundGid(20, 20).toLong())
    assertEquals(3, md1.getGroundGid(10, 30).toLong())
    assertEquals(4, md1.getGroundGid(20, 30).toLong())
    assertEquals(5, md1.getGroundGid(15, 25).toLong())
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun putGroundLayer_notInDtoRect_throws() {

    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)

    md1.putLayer(0, 5, 20, 1337)
  }

  @Test
  fun getRect_correctRect() {
    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)
    assertEquals(r1, md1.rect)
  }

  @Test
  fun slice_containingRect_ok() {
    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)
    md1.putGroundLayer(10, 20, 1)
    md1.putGroundLayer(12, 20, 2)
    md1.putGroundLayer(20, 21, 2)
    md1.putGroundLayer(19, 21, 1)
    md1.putLayer(1, 10, 20, 4)
    md1.putLayer(1, 14, 23, 5)
    md1.putLayer(1, 16, 23, 6)

    val sliceRect = Rect(10, 20, 5, 5)
    val sliced = md1.slice(sliceRect)
    val GIDS = HashSet(Arrays.asList(1, 2, 4, 5))

    assertEquals(sliceRect, sliced.rect)
    assertEquals(GIDS, sliced.distinctGids)
    assertThat(sliced.distinctGids, not(contains(6)))
    assertEquals(1, sliced.getGroundGid(10, 20).toLong())

    assertThat(sliced.getLayerGids(10, 20), contains(4))
    assertThat(sliced.getLayerGids(10, 20), not(contains(6)))
  }

  @Test(expected = IllegalArgumentException::class)
  fun slice_outOfBoundsRect_throws() {
    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)
    val sliceRect = Rect(18, 20, 8, 8)
    md1.slice(sliceRect)
  }

  @Test
  fun getDistinctGids_distinctGids() {
    val r1 = Rect(10, 20, 10, 10)
    val md1 = MapDataDTO(r1)

    md1.putGroundLayer(10, 20, 1)
    md1.putGroundLayer(20, 20, 2)
    md1.putGroundLayer(10, 30, 2)
    md1.putGroundLayer(20, 30, 1)
    md1.putGroundLayer(15, 25, 5)
    md1.putLayer(1, 14, 23, 4)
    md1.putLayer(2, 14, 23, 6)

    val GIDS = HashSet(Arrays.asList(1, 2, 4, 5, 6))
    assertEquals(GIDS, md1.distinctGids)
  }


  @Test
  fun join_rightLeftAdjacentDTO_ok() {

    val r1 = Rect(20, 20, 10, 10)
    val r2 = Rect(31, 20, 10, 10)

    val md1 = MapDataDTO(r1)
    val md2 = MapDataDTO(r2)

    fillGid(md1, 1)
    fillGid(md2, 2)

    val joined1 = md1.join(md2)
    val joined2 = md2.join(md1)

    val joinedRect = Rect(20, 20, 20, 10)

    Assert.assertEquals(joinedRect, joined1.rect)
    Assert.assertEquals(joinedRect, joined2.rect)
    Assert.assertEquals(joined2.rect, joined2.rect)

    assertEquals(IDS1, joined1.getLayerGids(22, 23))
    assertEquals(IDS1, joined2.getLayerGids(22, 23))
    assertEquals(IDS2, joined1.getLayerGids(25, 5))
    assertEquals(IDS2, joined2.getLayerGids(25, 5))

    // Check gids
    assertEquals(1, joined1.getGroundGid(20, 31).toLong())
    assertEquals(1, joined2.getGroundGid(20, 31).toLong())
    assertEquals(2, joined1.getGroundGid(20, 10).toLong())
    assertEquals(2, joined2.getGroundGid(20, 10).toLong())
  }

  @Test
  fun join_topBottomtAdjacentDTO_ok() {

    val r1 = Rect(20, 21, 10, 10)
    val r2 = Rect(20, 10, 10, 10)

    val md1 = MapDataDTO(r1)
    val md2 = MapDataDTO(r2)

    // Fill GIDs
    fillGid(md1, 1)
    fillGid(md2, 2)

    // Fill sparse layers
    md1.putLayer(1, 22, 23, 10)
    md1.putLayer(2, 22, 23, 11)
    md2.putLayer(1, 25, 5, 12)

    val joined1 = md1.join(md2)
    val joined2 = md2.join(md1)

    val joinedRect = Rect(20, 10, 10, 20)

    Assert.assertEquals(joinedRect, joined1.rect)
    Assert.assertEquals(joinedRect, joined2.rect)
    Assert.assertEquals(joined2.rect, joined2.rect)

    assertEquals(IDS1, joined1.getLayerGids(22, 23))
    assertEquals(IDS1, joined2.getLayerGids(22, 23))
    assertEquals(IDS2, joined1.getLayerGids(25, 5))
    assertEquals(IDS2, joined2.getLayerGids(25, 5))

    // Check gids
    assertEquals(1, joined1.getGroundGid(20, 31).toLong())
    assertEquals(1, joined2.getGroundGid(20, 31).toLong())
    assertEquals(2, joined1.getGroundGid(20, 10).toLong())
    assertEquals(2, joined2.getGroundGid(20, 10).toLong())
  }

  private fun fillGid(dto: MapDataDTO, gid: Int) {

    val maxY = dto.rect.y + dto.rect.height
    val maxX = dto.rect.x + dto.rect.width

    for (y in dto.rect.y until maxY) {
      for (x in dto.rect.x until maxX) {
        dto.putGroundLayer(x, y, gid)
      }
    }
  }

  companion object {

    private val IDS1 = Arrays.asList(10, 11)
    private val IDS2 = Arrays.asList(12)
  }

}
