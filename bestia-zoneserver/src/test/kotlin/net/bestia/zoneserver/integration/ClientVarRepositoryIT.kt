package net.bestia.zoneserver.integration

import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.ClientVar
import net.bestia.model.account.ClientVarRepository
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class ClientVarRepositoryIT {

  @Autowired
  private lateinit var clientVarRepository: ClientVarRepository

  @Autowired
  private lateinit var accountRepository: AccountRepository

  @Test
  fun test() {
    val acc = Account.test()
    accountRepository.save(acc)

    val sut = ClientVar(acc, "key", "abc123")
    clientVarRepository.save(sut)

    val found = clientVarRepository.findByKeyAndAccountId("key", acc.id)
    Assert.assertNotNull(found)
    Assert.assertEquals("abc123", found!!.getDataAsString())
  }
}