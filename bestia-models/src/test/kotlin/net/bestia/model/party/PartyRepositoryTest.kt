package net.bestia.model.party

import net.bestia.model.IntegrationTest
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.annotation.BeforeTestExecution

@IntegrationTest
class PartyRepositoryTest {

  @Autowired
  private lateinit var partyRepository: PartyRepository

  @Autowired
  private lateinit var accountRepository: AccountRepository

  @BeforeTestExecution
  fun setup() {
    val a = Account.test()
    accountRepository.save(a)

    val p = Party("party")
    p.addMember(a)

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
