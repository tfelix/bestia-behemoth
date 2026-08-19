package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto

/**
 * Generic wrapper for the shared `OperationError` wire message. Reuse this for a new denial
 * reason instead of adding another per-feature *ErrorSMSG class - just add the value to
 * `OpError` in operation_error.proto and send it through here. Only reach for a dedicated SMSG
 * (like [net.bestia.zone.party.PartyErrorSMSG]) when the error genuinely needs its own payload
 * beyond a single code.
 */
data class OperationErrorSMSG(
  val code: OperationErrorProto.OpError,

  /**
   * Substitution values for the client's message template, in order - a player's name, a count, a place.
   *
   * Deliberately values and not a sentence: the wording and its translation belong to the client, and passing
   * a composed string would turn a code into English on the wire. Most codes carry none.
   */
  val args: List<String> = emptyList()
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val opError = OperationErrorProto.OperationError.newBuilder()
      .setCode(code)
      .addAllArgs(args)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationError(opError)
      .build()
  }
}
