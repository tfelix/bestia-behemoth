package net.bestia.zone.message

import net.bestia.zone.BestiaEvent
import net.bestia.bnet.proto.EnvelopeProto.Envelope

/**
 * Probably the most important event as this contains the envelope of a player socket datagram which was received
 * by an authenticated client.
 */
class MessageEnvelopeReceivedEvent(
  source: Any,
  val senderAccountId: Long,
  val envelope: Envelope
) : BestiaEvent(source)