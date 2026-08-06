package net.bestia.zone.account

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * Materializes a zone-local account row the first time an account connects to this zone.
 *
 * A zone never learns about accounts any other way: the only thing it is handed is a login-server JWT,
 * and the two servers share no database. With several zones a player may be routed to any of them, and
 * to a zone the player has never visited that is indistinguishable from a brand new account — so
 * "account exists here" cannot be a precondition for connecting, it has to be established on connect.
 *
 * Runs before every other [AccountConnectedEvent] listener so that anything downstream (and any request
 * the client fires off the moment it sees `AuthenticationSuccess`, master discovery being the first one)
 * finds the row already there.
 */
@Service
class AccountProvisioningService(
  private val accountRepository: AccountRepository,
  private val accountFactory: AccountFactory
) {

  /**
   * Deliberately not `@Transactional`. The check-then-insert cannot be made atomic against another
   * zone-server instance anyway, so correctness rests on the unique index on `loginAccountId` and the
   * catch below — and a rolled-back surrounding transaction would leave that catch unable to read.
   */
  @EventListener
  @Order(Ordered.HIGHEST_PRECEDENCE)
  fun handleAccountConnected(event: AccountConnectedEvent) {
    if (accountRepository.findByLoginAccountId(event.accountId) != null) {
      return
    }

    val account = try {
      accountFactory.createAccount(event.accountId)
    } catch (e: DataIntegrityViolationException) {
      // Two connections for the same never-before-seen account can both miss the lookup above; the
      // unique index makes exactly one of them lose. Losing is a success: the row it wanted now exists.
      LOG.debug(e) { "Lost the race to create the account for login account ${event.accountId}" }

      accountRepository.findByLoginAccountId(event.accountId) ?: throw e
    }

    LOG.info {
      "Created zone account ${account.id} for login account ${event.accountId} on its first connect to this zone"
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
