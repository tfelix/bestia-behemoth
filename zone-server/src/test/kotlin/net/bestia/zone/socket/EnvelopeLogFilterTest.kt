package net.bestia.zone.socket

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PathComponentSMSGProto
import net.bestia.bnet.proto.PositionComponentProto
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which envelopes the wire log keeps, now decided from the message type rather than from its rendered text.
 *
 * The substring case below is the one worth pinning: it passed silently before, because an entry was tested
 * against the whole dump, so `!comp_path` also suppressed any message that merely mentioned it.
 */
class EnvelopeLogFilterTest {

  private val position = EnvelopeProto.Envelope.newBuilder()
    .setCompPosition(PositionComponentProto.PositionComponent.getDefaultInstance())
    .build()

  private val path = EnvelopeProto.Envelope.newBuilder()
    .setCompPath(PathComponentSMSGProto.PathComponentSMSG.getDefaultInstance())
    .build()

  @Test
  fun `no entries allows everything`() {
    val filter = EnvelopeLogFilter(emptyList())

    assertTrue(filter.allows(position))
    assertTrue(filter.allows(path))
  }

  @Test
  fun `a bare entry excludes everything it does not name`() {
    val filter = EnvelopeLogFilter(listOf("comp_path"))

    assertTrue(filter.allows(path))
    assertFalse(filter.allows(position))
  }

  @Test
  fun `a denied entry drops just that type`() {
    val filter = EnvelopeLogFilter(listOf("!comp_position"))

    assertTrue(filter.allows(path))
    assertFalse(filter.allows(position))
  }

  @Test
  fun `a denied entry wins over an allowing one`() {
    val filter = EnvelopeLogFilter(listOf("comp_path", "!comp_path"))

    assertFalse(filter.allows(path))
  }

  @Test
  fun `a type the configuration does not mention is unaffected by a denial`() {
    val filter = EnvelopeLogFilter(listOf("!comp"))

    assertTrue(filter.allows(position))
    assertTrue(filter.allows(path))
  }

  @Test
  fun `an envelope carrying no message is not mistaken for a named type`() {
    val filter = EnvelopeLogFilter(listOf("!comp_position"))

    assertTrue(filter.allows(EnvelopeProto.Envelope.getDefaultInstance()))
  }
}
