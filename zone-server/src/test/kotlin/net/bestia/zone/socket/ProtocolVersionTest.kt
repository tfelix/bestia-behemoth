package net.bestia.zone.socket

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.session.AccountConnectedEvent
import net.bestia.zone.message.UnknownBnetMessageException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolVersionTest {

  private val client = ClientHandlerFixture()

  @Test
  fun `a client of another protocol version is refused before its token is checked`() {
    client.authenticate(protocolVersion = ClientHandlerFixture.CURRENT_PROTOCOL_VERSION + 1)

    assertEquals("PROTOCOL_MISMATCH", client.disconnectReason())
    assertFalse(client.channel.isOpen)
    assertTrue(client.events.none { it is AccountConnectedEvent })
  }

  @Test
  fun `a client built before versioning sends no version and is refused`() {
    client.authenticate(protocolVersion = 0)

    assertEquals("PROTOCOL_MISMATCH", client.disconnectReason())
  }

  @Test
  fun `a client of the current version is let in`() {
    client.authenticate()

    assertNull(client.disconnectReason())
    assertTrue(client.channel.isOpen)
  }

  @Test
  fun `a message the zone cannot read ends the connection with a reason`() {
    val client = ClientHandlerFixture(onMessage = { throw UnknownBnetMessageException(it.envelope) })
    client.authenticate()

    // An envelope with no case set: what a newer client's message looks like to this zone.
    client.channel.writeInbound(EnvelopeProto.Envelope.getDefaultInstance())

    assertEquals("UNKNOWN_MESSAGE", client.disconnectReason())
  }
}
