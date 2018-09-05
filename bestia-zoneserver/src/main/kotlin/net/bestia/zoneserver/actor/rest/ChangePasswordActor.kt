package net.bestia.zoneserver.actor.rest

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.account.ChangePasswordRequest
import net.bestia.zoneserver.client.AccountService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Changes the password of a user.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class ChangePasswordActor(
        private val accService: AccountService
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ChangePasswordRequest::class.java, this::handleChangePassword)
            .build()
  }

  private fun handleChangePassword(data: ChangePasswordRequest) {
    LOG.debug("Check data: {}", data)

    val wasSuccess = accService.changePassword(data.accountName,
            data.oldPassword,
            data.newPassword)

    // Reply the message.
    sender.tell(wasSuccess, self)
  }

  companion object {
    const val NAME = "RESTchangePassword"
  }
}
