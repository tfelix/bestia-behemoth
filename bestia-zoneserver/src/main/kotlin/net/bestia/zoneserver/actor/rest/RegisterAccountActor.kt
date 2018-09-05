package net.bestia.zoneserver.actor.rest

import akka.actor.AbstractActor
import net.bestia.messages.account.AccountRegistration
import net.bestia.zoneserver.client.AccountService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Performs an account registration procedure.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class RegisterAccountActor(
        private val accService: AccountService
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(AccountRegistration::class.java, this::handleRegister)
            .build()
  }

  private fun handleRegister(data: AccountRegistration) {
    accService.createNewAccount(data)
  }

  companion object {
    const val NAME = "RESTregisterAccount"
  }
}
