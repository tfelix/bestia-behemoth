package net.bestia.zoneserver.map

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.map.TilesetData
import net.bestia.model.map.TilesetDataRepository
import net.bestia.zoneserver.config.JacksonConfiguration
import org.junit.Assert.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class TilesetServiceTest {

  private lateinit var tilesetServ: TilesetService

  @Mock
  private lateinit var validData: TilesetData

  @Mock
  private lateinit var invalidData: TilesetData

  @Mock
  private lateinit var tilesetDao: TilesetDataRepository

  @BeforeEach
  fun setup() {
    tilesetServ = TilesetService(JacksonConfiguration.createBestiaMapper(), tilesetDao)
  }

  @Test
  fun findTileset_unknownId_empty() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)

    val ts = tilesetServ.findTileset(INVALID_ID)
    assertNull(ts)
  }

  @Test
  fun findTileset_knownIdValidData_notEmpty() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)
    whenever(tilesetDao.findByGid(VALID_ID.toLong())).thenReturn(validData)

    whenever(validData.data).thenReturn(VALID_DATA)

    val ts = tilesetServ.findTileset(VALID_ID)
    assertNotNull(ts)
  }

  @Test
  fun findTileset_knownIdInvalidData_empty() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)
    whenever(tilesetDao.findByGid(INVALID_DATA_ID.toLong())).thenReturn(invalidData)

    whenever(invalidData.data).thenReturn(INVALID_DATA)

    val ts = tilesetServ.findTileset(INVALID_DATA_ID)
    assertNull(ts)
  }

  @Test
  fun findAllTilesets_listWithKnownIds_results() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)
    whenever(tilesetDao.findByGid(VALID_ID.toLong())).thenReturn(validData)

    whenever(validData.data).thenReturn(VALID_DATA)

    val ids = HashSet(Arrays.asList(VALID_ID))
    val ts = tilesetServ.findAllTilesets(ids)

    assertEquals(1, ts.size.toLong())
  }

  @Test
  fun findAllTilesets_listWithUnknwonIds_emptyList() {
    whenever(tilesetDao.findByGid(anyLong())).thenReturn(null)

    val ids = HashSet(Arrays.asList(INVALID_ID))
    val ts = tilesetServ.findAllTilesets(ids)

    assertEquals(0, ts.size.toLong())
  }

  companion object {
    private const val INVALID_ID = 10
    private const val VALID_ID = 11
    private const val INVALID_DATA_ID = 12

    private val VALID_DATA = """{
      "mingid": 0,
      "maxgid": 200,
      "size": {"width": 10, "height": 20}, "name": "mountain_landscape_23", "props": null
    }""".trimIndent()

    private const val INVALID_DATA = "{mingid: 0, maxgid: 200, name: 'mountain_landscape_23'}"
  }
}
