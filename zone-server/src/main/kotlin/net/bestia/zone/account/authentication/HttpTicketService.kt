package net.bestia.zone.account.authentication

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The credential the client's HTTP requests carry, minted per socket session.
 *
 * Named for what it authenticates rather than for its one current caller: the map is the only thing served
 * over HTTP today, but nothing about a session credential is map-shaped, and anything else put behind HTTP
 * wants this same ticket rather than a second scheme of its own.
 *
 * ### Why not the login token
 *
 * That was the first answer and it was wrong in a way worth recording, because the mistake is inviting: the
 * client already holds a signed token, so reusing it looks free. But a login token expires - an hour after
 * it was issued, in this deployment - while a session does not, and the socket only judges it once at the
 * handshake. So an hour into play the game carried on working perfectly and *every* map request started
 * coming back 401, with the client having no way to tell an expired token from a forged one.
 *
 * A ticket has no expiry of its own because it does not need one: it is forgotten when the connection that
 * owns it goes away, which is the only lifetime an HTTP caller ever wanted. That it is opaque rather than
 * signed is the same reasoning - there is nothing to verify when the store *is* the authority.
 *
 * The ticket names an *account*, never a master. A caller that needs to know which master is asking reads
 * that from the live session, so a client cannot act as a character it is not playing by editing a request.
 *
 * ### Its own store rather than a field on the session
 *
 * This is read from Tomcat's thread pool and written from Netty's event loops, with no world-thread
 * convention to lean on - so it holds its own concurrent maps. `ConnectionInfoService` also never removes
 * its entries, and a credential has to actually go away when the connection does.
 */
@Service
class HttpTicketService {

  private val byAccount = ConcurrentHashMap<Long, String>()
  private val byTicket = ConcurrentHashMap<String, Long>()

  /**
   * This account's ticket, minting one if it has none.
   *
   * Mint-or-return rather than always-mint so the handshake can put the ticket on the reply *before*
   * flushing it, and the [AccountConnectedEvent] that follows is then a no-op rather than a second ticket
   * that invalidates the one the client was just handed.
   */
  fun ticketFor(accountId: Long): String {
    return byAccount.computeIfAbsent(accountId) {
      val newTicket = UUID.randomUUID().toString()
      byTicket[newTicket] = accountId
      LOG.debug { "Minted an HTTP ticket for account $accountId" }

      newTicket
    }
  }

  /** The account holding [ticket], or null if no live session presented it. */
  fun accountFor(ticket: String): Long? {
    return byTicket[ticket]
  }

  /**
   * Drops the connection's ticket.
   *
   * Eagerly, for the reason [net.bestia.zone.world.stream.WorldInfoSender] drops its streaming state
   * eagerly: a reconnect must not be able to inherit the old connection's credential.
   */
  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    byAccount.remove(event.accountId)?.let { byTicket.remove(it) }
  }

  /**
   * Covers the paths that never run the socket handshake - the test doubles, and any future gateway that
   * announces a connection rather than accepting one itself. Not redundant with the handshake's own mint,
   * which is why [ticketFor] is mint-or-return.
   */
  @EventListener
  fun handleAccountConnected(event: AccountConnectedEvent) {
    ticketFor(event.accountId)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
