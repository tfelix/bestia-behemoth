package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.SMSG

/** Why a change of orders was refused. */
data class AiConfigErrorSMSG(
  val error: AiConfigErrorCode,
) : SMSG {

  enum class AiConfigErrorCode {
    /** The named bestia is not one of this player's. */
    BESTIA_NOT_OWNED,

    /**
     * The job or courage step was unset or unknown to this server. There is no nearest valid job, so it is
     * refused rather than quietly turned into a default the player did not choose.
     */
    INVALID_ORDERS,
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val mapped = when (error) {
      AiConfigErrorCode.BESTIA_NOT_OWNED -> OperationErrorProto.OpError.AI_CONFIG_BESTIA_NOT_OWNED
      AiConfigErrorCode.INVALID_ORDERS -> OperationErrorProto.OpError.AI_CONFIG_INVALID_ORDERS
    }

    val opError = OperationErrorProto.OperationError.newBuilder().setCode(mapped)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationError(opError)
      .build()
  }
}
