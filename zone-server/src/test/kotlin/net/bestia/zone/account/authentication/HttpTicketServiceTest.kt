package net.bestia.zone.account.authentication

import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The ticket's lifetime, which is the whole point of it existing.
 *
 * Plain unit tests: the service holds two maps and listens to two events, and nothing about that needs a
 * Spring context to be interesting.
 */
class HttpTicketServiceTest {

  private val service = HttpTicketService()

  @Test
  fun `asking twice gives the same ticket`() {
    // Mint-or-return, not always-mint: the handshake asks for one to put on the reply and the
    // AccountConnectedEvent that follows asks again, and a second ticket would invalidate the one the client
    // was just handed.
    val first = service.ticketFor(ACCOUNT)

    assertEquals(first, service.ticketFor(ACCOUNT))
  }

  @Test
  fun `a ticket resolves back to its account`() {
    val ticket = service.ticketFor(ACCOUNT)

    assertEquals(ACCOUNT, service.accountFor(ticket))
  }

  @Test
  fun `a ticket nobody was issued resolves to nothing`() {
    assertNull(service.accountFor("not-a-ticket"))
  }

  @Test
  fun `connecting mints a ticket for the paths that never run the handshake`() {
    service.handleAccountConnected(AccountConnectedEvent(this, ACCOUNT, emptySet()))

    assertEquals(ACCOUNT, service.accountFor(service.ticketFor(ACCOUNT)))
  }

  @Test
  fun `disconnecting forgets the ticket`() {
    val ticket = service.ticketFor(ACCOUNT)

    service.handleAccountDisconnected(AccountDisconnectedEvent(this, ACCOUNT))

    assertNull(service.accountFor(ticket))
  }

  @Test
  fun `reconnecting is a different ticket`() {
    // So a client that kept the old one cannot present it, and so the ticket cannot be the long-lived thing
    // the login token turned out to be.
    val before = service.ticketFor(ACCOUNT)
    service.handleAccountDisconnected(AccountDisconnectedEvent(this, ACCOUNT))

    assertNotEquals(before, service.ticketFor(ACCOUNT))
  }

  @Test
  fun `one account disconnecting leaves another's ticket alone`() {
    val mine = service.ticketFor(ACCOUNT)
    val theirs = service.ticketFor(OTHER_ACCOUNT)

    service.handleAccountDisconnected(AccountDisconnectedEvent(this, OTHER_ACCOUNT))

    assertEquals(ACCOUNT, service.accountFor(mine))
    assertNull(service.accountFor(theirs))
  }

  private companion object {
    const val ACCOUNT = 42L
    const val OTHER_ACCOUNT = 43L
  }
}
