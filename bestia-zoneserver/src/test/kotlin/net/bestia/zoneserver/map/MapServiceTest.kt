package net.bestia.zoneserver.map

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect
import net.bestia.model.map.MapDataRepository
import net.bestia.model.map.MapParameter
import net.bestia.model.map.MapParameterRepository
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class MapServiceTest {

  private lateinit var ms: MapService

  @Mock
  private lateinit var dataNoMapDao: MapDataRepository

  @Mock
  private lateinit var dataMapDao: MapDataRepository

  @Mock
  private lateinit var paramDao: MapParameterRepository

  @Mock
  private lateinit var paramNoMapDao: MapParameterRepository

  @Mock
  private lateinit var mapParams: MapParameter

  @Mock
  private lateinit var tilesetService: TilesetService

  @Before
  fun setup() {
    whenever(dataMapDao.count()).thenReturn(1L)
    whenever(mapParams.name).thenReturn(MAP_NAME)
    whenever(paramDao.findFirstByOrderByIdDesc()).thenReturn(mapParams)

    ms = MapService(dataNoMapDao, paramDao, tilesetService)
  }

  @Test
  fun isMapInitialized_noMapInsideDB_false() {
    Assert.assertFalse(ms.isMapInitialized)
  }

  @Test
  fun isMapInitialized_mapInsideDB_true() {
    ms = MapService(dataMapDao, paramDao, tilesetService)
    Assert.assertTrue(ms.isMapInitialized)
  }

  @Test(expected = IllegalArgumentException::class)
  fun getMap_illegalCoordinates_throws() {
    ms.getMap(10, 10, -10, 10)
  }

  @Test
  fun getMap_legalCoordinates_validMap() {

    val m = ms.getMap(5, 10, 10, 10)

    Assert.assertNotNull(m)
    Assert.assertEquals(Rect(5, 10, 10, 10), m.rect)
  }

  @Test
  fun getMapName_noMapInsideDB_emptyStr() {
    ms = MapService(dataNoMapDao, paramNoMapDao, tilesetService)
    Assert.assertEquals("", ms.mapName)
  }

  @Test
  fun getMapName_mapInsideDB_validStr() {
    Assert.assertEquals(MAP_NAME, ms.mapName)
  }

  @Test
  fun getChunks_validCords_listWithChunks() {
    val chunkCords = ArrayList<Point>()
    chunkCords.add(Point(1, 1))
    val chunks = ms.getChunks(chunkCords)

    Assert.assertNotNull(chunks)
    Assert.assertEquals(1, chunks.size.toLong())
  }

  companion object {
    private const val MAP_NAME = "kalarian"
  }
}
