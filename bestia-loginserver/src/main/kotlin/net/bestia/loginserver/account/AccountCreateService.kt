package net.bestia.loginserver.account

import mu.KotlinLogging
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class AccountCreateService(
    private val accountFactory: AccountFactory,
    private val masterFactory: PlayerMasterFactory,
    private val playerItemFactory: PlayerItemFactory
) {

  fun createAccount(newAccount: AccountCreateModel) {
    val account = accountFactory.createAccount(newAccount)

    masterFactory.createBestiaMaster(account, newAccount)
    playerItemFactory.addStarterItems(account)
  }
}