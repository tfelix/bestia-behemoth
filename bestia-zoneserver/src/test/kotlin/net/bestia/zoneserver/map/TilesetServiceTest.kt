package net.bestia.zoneserver.map

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.map.TilesetData
import net.bestia.model.map.TilesetDataRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.*
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class TilesetServiceTest {

  private lateinit var tilesetServ: TilesetService

  @Mock
  private lateinit var validData: TilesetData

  @Mock
  private lateinit var invalidData: TilesetData

  @Mock
  private lateinit var tilesetDao: TilesetDataRepository

  @Before
  fun setup() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)
    whenever(tilesetDao.findByGid(VALID_ID.toLong())).thenReturn(validData)
    whenever(tilesetDao.findByGid(INVALID_DATA_ID.toLong())).thenReturn(invalidData)

    whenever(validData.data).thenReturn(VALID_DATA)
    whenever(invalidData.data).thenReturn(INVALID_DATA)

    tilesetServ = TilesetService(ObjectMapper(), tilesetDao)
  }

  @Test
  fun findTileset_unknownId_empty() {
    val ts = tilesetServ.findTileset(INVALID_ID)
    assertNotNull(ts)
  }

  @Test
  fun findTileset_knownIdValidData_notEmpty() {
    val ts = tilesetServ.findTileset(VALID_ID)
    assertNotNull(ts)
  }

  @Test
  fun findTileset_knownIdInValidData_empty() {
    val ts = tilesetServ.findTileset(INVALID_DATA_ID)
    assertNotNull(ts)
  }

  @Test
  fun findAllTilesets_listWithKnownIds_results() {
    val ids = HashSet(Arrays.asList(VALID_ID))
    val ts = tilesetServ.findAllTilesets(ids)

    assertEquals(1, ts.size.toLong())
  }

  @Test
  fun findAllTilesets_listWithUnknwonIds_emptyList() {
    val ids = HashSet(Arrays.asList(INVALID_ID))
    val ts = tilesetServ.findAllTilesets(ids)

    assertEquals(0, ts.size.toLong())
  }

  companion object {
    private const val INVALID_ID = 10
    private const val VALID_ID = 11
    private const val INVALID_DATA_ID = 12

    private const val VALID_DATA = "{\"mingid\": 0, \"maxgid\": 200, \"size\": {\"width\": 10, \"height\": 20}, \"name\": \"mountain_landscape_23\", \"props\": null}"
    private const val INVALID_DATA = "{mingid: 0, maxgid: 200, name: 'mountain_landscape_23'}"
  }
}
