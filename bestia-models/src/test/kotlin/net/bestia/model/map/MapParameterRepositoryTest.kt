package net.bestia.model.map

import net.bestia.model.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class MapParameterRepositoryTest {

  @Autowired
  private val dao: MapParameterRepository? = null

  @BeforeEach
  fun setup() {
    val p1 = MapParameter.fromAverageUserCount(100, MAPNAME1)
    dao!!.save(p1)

    val p2 = MapParameter.fromAverageUserCount(110, MAPNAME2)
    dao.save(p2)
  }

  @Test
  fun findLatest_latestParams() {
    val p = dao!!.findFirstByOrderByIdDesc()
    assertEquals(MAPNAME2, p!!.name)
  }

  companion object {
    private const val MAPNAME1 = "Ballermann"
    private const val MAPNAME2 = "Ballermann 2"
  }
}
