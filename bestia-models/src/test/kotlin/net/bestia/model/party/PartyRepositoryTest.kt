package net.bestia.model.party

import net.bestia.model.IntegrationTest
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.test.AccountFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class PartyRepositoryTest {

  @Autowired
  private lateinit var partyRepository: PartyRepository

  @BeforeEach
  fun setup() {
    val p = Party("party")
    partyRepository.save(p)
  }

  @Test
  fun findPartyByMembership_accountIsMember_party() {
    val p = partyRepository.findPartyByMembership(ACC_ID)
    assertNotNull(p)
  }

  companion object {
    private const val ACC_ID = 123L
  }
}
