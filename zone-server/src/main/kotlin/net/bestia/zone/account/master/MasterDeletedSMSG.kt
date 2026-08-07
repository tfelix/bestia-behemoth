package net.bestia.zone.account.master

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationSuccessProto

object MasterDeletedSMSG : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val opSuccess = OperationSuccessProto.OperationSuccess.newBuilder()
      .setCode(OperationSuccessProto.OpSuccess.MASTER_DELETED)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationSuccess(opSuccess)
      .build()
  }
}
