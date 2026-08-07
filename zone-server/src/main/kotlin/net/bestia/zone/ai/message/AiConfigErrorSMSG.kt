package net.bestia.zone.ai.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.SMSG

/** Why a standing-order change was refused. */
data class AiConfigErrorSMSG(
  val error: AiConfigErrorCode,
) : SMSG {

  enum class AiConfigErrorCode {
    /** The named bestia is not one of this player's. */
    BESTIA_NOT_OWNED,

    /**
     * The stance was unset or unknown to this server.
     *
     * Unlike the numeric knobs this cannot be clamped — there is no nearest valid stance — so it is refused
     * rather than quietly turned into a default the player did not choose.
     */
    INVALID_STANCE,
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val mapped = when (error) {
      AiConfigErrorCode.BESTIA_NOT_OWNED -> OperationErrorProto.OpError.AI_CONFIG_BESTIA_NOT_OWNED
      AiConfigErrorCode.INVALID_STANCE -> OperationErrorProto.OpError.AI_CONFIG_INVALID_STANCE
    }

    val opError = OperationErrorProto.OperationError.newBuilder().setCode(mapped)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationError(opError)
      .build()
  }
}
