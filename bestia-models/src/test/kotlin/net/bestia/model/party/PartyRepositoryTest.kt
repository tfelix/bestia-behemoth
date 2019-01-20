package net.bestia.model.party

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@DataJpaTest
class PartyRepositoryTest {

  @Autowired
  private lateinit var dao: PartyRepository

  @Test
  fun findPartyByMembership_accountIsMember_party() {
    val p = dao.findPartyByMembership(ACC_ID)
    Assert.assertNotNull(p)
  }

  companion object {
    private const val ACC_ID = 123L
  }
}
