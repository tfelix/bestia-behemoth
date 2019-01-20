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
class TilesetDataRepositoryTest {

  @Autowired
  private lateinit var sut: TilesetDataRepository

  @Before
  fun setup() {
    val ts = TilesetData(
        maxGid = 100,
        minGid = 0,
        data = ""
    )
    sut.save(ts)
  }

  @Test
  fun findByGid_existingGid_returns() {
    val ts1 = sut.findByGid(10)
    val ts2 = sut.findByGid(100)

    Assert.assertNotNull(ts1)
    Assert.assertEquals(ts1, ts2)
  }

  @Test
  fun findByGid_outOfRangeGid_null() {
    val ts1 = sut.findByGid(101)
    Assert.assertNull(ts1)
  }
}
