package net.bestia.zoneserver.actor.rest

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.account.AccountLoginRequest
import net.bestia.zoneserver.client.LoginService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Performs a login operation and generates a new login token for the account.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class RequestLoginActor(
        private val loginService: LoginService
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(AccountLoginRequest::class.java, this::handleLogin)
            .build()
  }

  private fun handleLogin(msg: AccountLoginRequest) {
    LOG.debug("Received incoming login: {}", msg)

    val newToken = loginService.setNewLoginToken(msg)

    sender.tell(newToken, self)
  }

  companion object {
    const val NAME = "RESTrequestLogin"
  }
}
