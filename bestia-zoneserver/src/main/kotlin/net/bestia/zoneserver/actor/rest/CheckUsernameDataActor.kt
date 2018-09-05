package net.bestia.zoneserver.actor.rest

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.account.UserNameCheck
import net.bestia.model.dao.AccountDAO
import net.bestia.model.domain.Account
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Checks if a username and email is available.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class CheckUsernameDataActor(
        private val accDao: AccountDAO
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(UserNameCheck::class.java, this::handleUserNameCheck)
            .build()
  }

  private fun handleUserNameCheck(data: UserNameCheck) {

    LOG.debug("Check data: {}", data)

    var acc: Account? = accDao.findByEmail(data.email)

    data.isEmailAvailable = acc == null

    acc = accDao.findByUsername(data.username)
    data.isUsernameAvailable = acc == null

    // Reply the message.
    sender.tell(data, self)
  }

  companion object {
    const val NAME = "RESTcheckUsername"
  }
}
