package net.bestia.model.account

import net.bestia.model.AbstractEntity
import java.time.Instant
import javax.persistence.Entity

/**
 * This domain object is used to make a login history of the accounts.
 *
 * @author Thomas Felix
 */
@Entity
data class LoginInfo(
    val account: Account,
    val eventDate: Instant = Instant.now(),
    val eventType: LoginEvent,
    val ip: String,
    val browserAgent: String
) : AbstractEntity() {

  enum class LoginEvent {
    LOGIN, LOGOUT
  }

  override fun toString(): String {
    return "LoginInfo[accId: ${account.id}, $eventDate, $eventType]"
  }
}
