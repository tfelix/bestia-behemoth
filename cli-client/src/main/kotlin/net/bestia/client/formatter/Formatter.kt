package net.bestia.client.formatter

import net.bestia.bnet.proto.EnvelopeProto


/**
 * Is responsible for formatting messages
 */
interface Formatter {
  fun canHandle(envelope: EnvelopeProto.Envelope): Boolean

  fun format(envelope: EnvelopeProto.Envelope): String
}

