package net.bestia.zone.socket

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.DamageEntitySMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which envelopes the wire log keeps, now decided from the message type rather than from its rendered text.
 *
 * The substring case below is the one worth pinning: it passed silently before, because an entry was tested
 * against the whole dump, so `!damage_entity` also suppressed any message that merely mentioned it.
 */
class EnvelopeLogFilterTest {

  private val state = EnvelopeProto.Envelope.newBuilder()
    .setStateBatch(StateBatchSmsgProto.StateBatchSMSG.getDefaultInstance())
    .build()

  private val damage = EnvelopeProto.Envelope.newBuilder()
    .setDamageEntity(DamageEntitySMSGProto.DamageEntitySMSG.getDefaultInstance())
    .build()

  @Test
  fun `no entries allows everything`() {
    val filter = EnvelopeLogFilter(emptyList())

    assertTrue(filter.allows(state))
    assertTrue(filter.allows(damage))
  }

  @Test
  fun `a bare entry excludes everything it does not name`() {
    val filter = EnvelopeLogFilter(listOf("damage_entity"))

    assertTrue(filter.allows(damage))
    assertFalse(filter.allows(state))
  }

  @Test
  fun `a denied entry drops just that type`() {
    val filter = EnvelopeLogFilter(listOf("!state_batch"))

    assertTrue(filter.allows(damage))
    assertFalse(filter.allows(state))
  }

  @Test
  fun `a denied entry wins over an allowing one`() {
    val filter = EnvelopeLogFilter(listOf("damage_entity", "!damage_entity"))

    assertFalse(filter.allows(damage))
  }

  @Test
  fun `a type the configuration does not mention is unaffected by a denial`() {
    val filter = EnvelopeLogFilter(listOf("!state"))

    assertTrue(filter.allows(state))
    assertTrue(filter.allows(damage))
  }

  @Test
  fun `an envelope carrying no message is not mistaken for a named type`() {
    val filter = EnvelopeLogFilter(listOf("!state_batch"))

    assertTrue(filter.allows(EnvelopeProto.Envelope.getDefaultInstance()))
  }
}
