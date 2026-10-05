package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.BestiaException

class UnknownBnetMessageException(message: String) : BestiaException(
  code = "UNKNOWN_BNET_MESSAGE",
  message = message
) {

  /** Names the message case and unknown field numbers only, because a message can carry a login token. */
  constructor(envelope: EnvelopeProto.Envelope) : this(
    "No translation found for ${envelope.messageCase}, unknown fields ${envelope.unknownFields.asMap().keys}"
  )
}
