package net.bestia.zone.message.processor

import net.bestia.zone.BestiaException
import net.bestia.bnet.proto.EnvelopeProto

class UnknownBnetMessageException(message: String) : BestiaException(
  code = "UNKNOWN_BNET_MESSAGE",
  message = message
) {

  constructor(envelope: EnvelopeProto.Envelope): this("No translation found for $envelope")
}