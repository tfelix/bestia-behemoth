package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationSuccessProto

/**
 * Generic wrapper for the shared `OperationSuccess` wire message, the mirror of [OperationErrorSMSG].
 *
 * Reuse this for a new "it worked" acknowledgement instead of adding another per-feature SMSG - add the value
 * to `OpSuccess` in operation_success.proto and send it through here. The two existing per-case classes
 * ([net.bestia.zone.account.master.MasterCreatedSMSG] and its sibling) predate this and say exactly the same
 * thing the long way round.
 */
data class OperationSuccessSMSG(
  val code: OperationSuccessProto.OpSuccess
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val opSuccess = OperationSuccessProto.OperationSuccess.newBuilder()
      .setCode(code)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationSuccess(opSuccess)
      .build()
  }
}
