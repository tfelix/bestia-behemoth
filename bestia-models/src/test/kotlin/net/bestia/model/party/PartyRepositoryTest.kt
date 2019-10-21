package net.bestia.model.party

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
@DataJpaTest
class PartyRepositoryTest {

  @Autowired
  private lateinit var dao: PartyRepository

  @Test
  fun findPartyByMembership_accountIsMember_party() {
    val p = dao.findPartyByMembership(ACC_ID)
    assertNotNull(p)
  }

  companion object {
    private const val ACC_ID = 123L
  }
}
