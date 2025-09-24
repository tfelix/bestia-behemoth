package net.bestia.zone.account.master

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto

data class MasterErrorSMSG(
  val error: MasterErrorCode
) : SMSG {

  enum class MasterErrorCode {
    NAME_ALREADY_TAKEN,
    MAX_MASTERS_REACHED,
    INVALID_NAME,
    GENERAL_ERROR
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val mappedErrorCode = when (error) {
      MasterErrorCode.NAME_ALREADY_TAKEN -> OperationErrorProto.OpError.MASTER_NAME_ALREADY_TAKEN
      MasterErrorCode.MAX_MASTERS_REACHED -> OperationErrorProto.OpError.MASTER_MAX_MASTERS_REACHED
      MasterErrorCode.INVALID_NAME -> OperationErrorProto.OpError.MASTER_INVALID_NAME
      MasterErrorCode.GENERAL_ERROR -> OperationErrorProto.OpError.MASTER_GENERAL_ERROR
    }

    val opError = OperationErrorProto.OperationError.newBuilder()
      .setCode(mappedErrorCode)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationError(opError)
      .build()
  }
}
