package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.BestiaException

class UnknownBnetMessageException(message: String) : BestiaException(
  code = "UNKNOWN_BNET_MESSAGE",
  message = message
) {

  constructor(envelope: EnvelopeProto.Envelope): this("No translation found for $envelope")
}