package net.bestia.model.map

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
class MapParameterRepositoryTest {

  @Autowired
  private val dao: MapParameterRepository? = null

  @Before
  fun setup() {
    val p1 = MapParameter.fromAverageUserCount(100, MAPNAME1)
    dao!!.save(p1)

    val p2 = MapParameter.fromAverageUserCount(110, MAPNAME2)
    dao.save(p2)
  }

  @Test
  fun findLatest_latestParams() {
    val p = dao!!.findFirstByOrderByIdDesc()
    Assert.assertEquals(MAPNAME2, p!!.name)
  }

  companion object {

    private val MAPNAME1 = "Ballermann"
    private val MAPNAME2 = "Ballermann 2"
  }
}
