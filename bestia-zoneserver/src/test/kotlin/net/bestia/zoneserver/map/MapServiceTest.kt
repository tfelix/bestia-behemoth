package net.bestia.zoneserver.map

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect
import net.bestia.model.map.MapDataRepository
import net.bestia.model.map.MapParameter
import net.bestia.model.map.MapParameterRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.lang.IllegalArgumentException
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

  @BeforeEach
  fun setup() {
        ms = MapService(dataNoMapDao, paramDao, tilesetService)
  }

  @Test
  fun isMapInitialized_noMapInsideDB_false() {
    Assertions.assertFalse(ms.isMapInitialized)
  }

  @Test
  fun isMapInitialized_mapInsideDB_true() {
    whenever(dataMapDao.count()).thenReturn(1L)

    ms = MapService(dataMapDao, paramDao, tilesetService)
    Assertions.assertTrue(ms.isMapInitialized)
  }

  @Test
  fun getMap_illegalCoordinates_throws() {
    Assertions.assertThrows(IllegalArgumentException::class.java) {
      ms.getMap(10, 10, -10, 10)
    }
  }

  @Disabled("We need a valid, serialized MapData here")
  @Test
  fun getMap_legalCoordinates_validMap() {
    whenever(dataMapDao.count()).thenReturn(1L)
    whenever(mapParams.name).thenReturn(MAP_NAME)
    whenever(paramDao.findFirstByOrderByIdDesc()).thenReturn(mapParams)

    val m = ms.getMap(5, 10, 10, 10)

    Assertions.assertNotNull(m)
    Assertions.assertEquals(Rect(5, 10, 10, 10), m.rect)
  }

  @Test
  fun getMapName_noMapInsideDB_emptyStr() {
    ms = MapService(dataNoMapDao, paramNoMapDao, tilesetService)
    Assertions.assertEquals("", ms.mapName)
  }

  @Test
  fun getMapName_mapInsideDB_validStr() {
    whenever(mapParams.name).thenReturn(MAP_NAME)
    whenever(paramDao.findFirstByOrderByIdDesc()).thenReturn(mapParams)

    Assertions.assertEquals(MAP_NAME, ms.mapName)
  }

  @Test
  fun getChunks_validCords_listWithChunks() {
    val chunkCords = ArrayList<Point>()
    chunkCords.add(Point(1, 1))
    val chunks = ms.getChunks(chunkCords)

    Assertions.assertNotNull(chunks)
    Assertions.assertEquals(1, chunks.size.toLong())
  }

  companion object {
    private const val MAP_NAME = "kalarian"
  }
}
