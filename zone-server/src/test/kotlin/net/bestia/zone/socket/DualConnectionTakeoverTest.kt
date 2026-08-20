package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.account.Authority
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.account.authentication.HttpTicketService
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An account may only ever have one live connection, and the newest one wins.
 *
 * This matters because session state and the account's master entity in the world are keyed by account
 * id alone. Two sockets authenticated as the same account would both resolve to the same entity and
 * issue conflicting commands for it, and — since a player may be routed to any zone and can start a
 * second client whenever they like — that is a situation a player can trigger on purpose.
 *
 * Driven through [ClientMessageHandler] on [EmbeddedChannel]s rather than real sockets: the whole point
 * is the *ordering* of registry mutation against the connect/disconnect events, which real sockets would
 * only reproduce non-deterministically.
 */
class DualConnectionTakeoverTest {

  private val accountId = 42L

  /** Records events in order, which is most of what these tests assert about. */
  private class RecordingPublisher : ApplicationEventPublisher {
    val events = mutableListOf<Any>()

    override fun publishEvent(event: Any) {
      events.add(event)
    }

    inline fun <reified T> countOf() = events.filterIsInstance<T>().size
  }

  private fun contextFor(
    registry: ChannelRegistry,
    publisher: ApplicationEventPublisher
  ): ClientMessageHandlerContext {
    val readiness = ZoneReadinessService().apply { markReady() }

    return ClientMessageHandlerContext(
      applicationEventPublisher = publisher,
      authProcessor = object : AuthenticationProcessor {
        override fun authenticate(msg: EnvelopeProto.Envelope) =
          AuthenticationProcessor.AuthenticationSuccess(accountId, setOf(Authority.MAP_MOVE))
      },
      socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList()),
      channelRegistry = registry,
      zoneReadinessService = readiness,
      httpTicketService = HttpTicketService(),
      version = "test"
    )
  }

  /** A connection that has completed the handshake, i.e. is registered and owns the account. */
  private fun connect(
    registry: ChannelRegistry,
    publisher: ApplicationEventPublisher
  ): EmbeddedChannel {
    val channel = EmbeddedChannel(ClientMessageHandler(contextFor(registry, publisher)))
    channel.writeInbound(authEnvelope())

    return channel
  }

  private fun authEnvelope(): EnvelopeProto.Envelope = EnvelopeProto.Envelope.newBuilder()
    .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken("irrelevant-the-processor-is-faked"))
    .build()

  /** Drains a channel's outbound queue looking for the disconnect notice. */
  private fun disconnectReasonOf(channel: EmbeddedChannel): String? {
    while (true) {
      val out = channel.readOutbound<EnvelopeProto.Envelope>() ?: return null
      if (out.hasDisconnected()) {
        return out.disconnected.reason
      }
    }
  }

  @Test
  fun `a second connection for the same account takes the account over and terminates the first`() {
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))
    val publisher = RecordingPublisher()

    val first = connect(registry, publisher)
    assertSame(first, registry.getChannel(accountId), "the first connection should hold the account")

    val second = connect(registry, publisher)

    assertSame(
      second, registry.getChannel(accountId),
      "the newest connection must hold the account, so outbound traffic follows the client the player is using"
    )
    assertEquals(
      "OTHER_CONNECTION", disconnectReasonOf(first),
      "the displaced client must be told why it was dropped rather than seeing the socket just die"
    )
    assertFalse(first.isActive, "the displaced connection must actually be closed, not merely deregistered")
    assertTrue(second.isActive)
  }

  @Test
  fun `the displaced session is torn down before the new connection is announced`() {
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))
    val publisher = RecordingPublisher()

    connect(registry, publisher)
    connect(registry, publisher)

    // Ordering is the whole point: AccountEntityControlService reacts to the disconnect by deactivating
    // the session and despawning the master. If that landed after the second connect event it would tear
    // down the incoming session instead of the outgoing one.
    val kinds = publisher.events.map { it::class.simpleName }
    assertEquals(
      listOf("AccountConnectedEvent", "AccountDisconnectedEvent", "AccountConnectedEvent"),
      kinds,
      "the displaced connection must be torn down between the two connects"
    )
  }

  @Test
  fun `the displaced connection closing later neither unregisters nor disconnects the new one`() {
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))
    val publisher = RecordingPublisher()

    val first = connect(registry, publisher)
    val second = connect(registry, publisher)

    // The displaced channel's own channelInactive runs whenever its event loop gets round to it - long
    // after the takeover. Both the registration and the disconnect event are keyed by account id only,
    // so without ownership checks this is where the live connection would silently lose its channel.
    first.close().sync()

    assertSame(
      second, registry.getChannel(accountId),
      "the displaced connection closing must not clear the live connection's registration"
    )
    assertEquals(
      1, publisher.countOf<AccountDisconnectedEvent>(),
      "the displaced connection must not publish a second teardown for an account it no longer owns"
    )
  }

  @Test
  fun `a normal disconnect still tears the account down`() {
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))
    val publisher = RecordingPublisher()

    val only = connect(registry, publisher)
    only.close().sync()

    assertNull(registry.getChannel(accountId), "a closed connection must not stay registered")
    assertEquals(
      1, publisher.countOf<AccountDisconnectedEvent>(),
      "the ownership check must not suppress the teardown of a connection that does own the account"
    )
  }
}
