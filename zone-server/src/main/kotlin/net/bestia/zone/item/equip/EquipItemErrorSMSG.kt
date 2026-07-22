package net.bestia.zone.item.equip

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.SMSG

/**
 * Tells the client why an equip/unequip request was refused. Sent alongside a re-push of the
 * authoritative [net.bestia.zone.ecs.item.Equipment] component, so the error is only the *reason* -
 * the state correction itself rides on the component sync. Shares the generic `OperationError`
 * envelope, exactly like [net.bestia.zone.account.master.MasterErrorSMSG].
 */
data class EquipItemErrorSMSG(
  val error: EquipmentService.Denial
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val mappedErrorCode = when (error) {
      EquipmentService.Denial.SLOT_NOT_AVAILABLE -> OperationErrorProto.OpError.EQUIP_SLOT_NOT_AVAILABLE
      EquipmentService.Denial.ITEM_NOT_FOUND -> OperationErrorProto.OpError.EQUIP_ITEM_NOT_FOUND
      EquipmentService.Denial.NOT_ALLOWED -> OperationErrorProto.OpError.EQUIP_NOT_ALLOWED
    }

    val opError = OperationErrorProto.OperationError.newBuilder()
      .setCode(mappedErrorCode)

    return EnvelopeProto.Envelope.newBuilder()
      .setOperationError(opError)
      .build()
  }
}
