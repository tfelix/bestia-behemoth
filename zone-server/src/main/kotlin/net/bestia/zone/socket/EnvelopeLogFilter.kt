package net.bestia.zone.socket

import net.bestia.bnet.proto.EnvelopeProto

/**
 * Selects which envelopes reach the wire log, by message type: a bare entry allows, a `!` prefix denies,
 * and no entries at all allows everything.
 *
 * Matching the type rather than the rendered message is what lets the decision happen before the envelope
 * is turned into a string - which is the whole cost the filter exists to avoid - and it also stops an entry
 * matching a nested value that merely happens to contain the same text.
 */
class EnvelopeLogFilter(
  entries: List<String>
) {

  private val allowed = entries.filterNot { it.startsWith("!") }.toSet()
  private val denied = entries.filter { it.startsWith("!") }.map { it.substring(1) }.toSet()

  fun allows(envelope: EnvelopeProto.Envelope): Boolean {
    val name = NAMES_BY_FIELD_NUMBER[envelope.messageCase.number] ?: UNKNOWN_TYPE

    return (allowed.isEmpty() || name in allowed) && name !in denied
  }

  companion object {
    private const val UNKNOWN_TYPE = "?"

    private val NAMES_BY_FIELD_NUMBER = EnvelopeProto.Envelope.getDescriptor().fields
      .associate { it.number to it.name }
  }
}
