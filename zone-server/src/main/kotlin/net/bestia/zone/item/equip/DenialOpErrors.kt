package net.bestia.zone.item.equip

import net.bestia.bnet.proto.OperationErrorProto

/**
 * The wire code for a refusal, shared by the two places one is sent: [EquipItemHandler] when the player asks
 * for something they may not have, and [EquipmentRevalidationService] when gear they already had stops being
 * theirs to wear. One mapping rather than two, so a new [EquipmentService.Denial] cannot reach the player as
 * the right sentence down one path and the wrong one down the other.
 */
fun EquipmentService.Denial.toOpError(): OperationErrorProto.OpError {
  return when (this) {
    EquipmentService.Denial.SLOT_NOT_AVAILABLE -> OperationErrorProto.OpError.EQUIP_SLOT_NOT_AVAILABLE
    EquipmentService.Denial.ITEM_NOT_FOUND -> OperationErrorProto.OpError.EQUIP_ITEM_NOT_FOUND
    EquipmentService.Denial.NOT_ALLOWED -> OperationErrorProto.OpError.EQUIP_NOT_ALLOWED
    EquipmentService.Denial.LEVEL_TOO_LOW -> OperationErrorProto.OpError.EQUIP_LEVEL_TOO_LOW
    EquipmentService.Denial.NOVICE_ONLY -> OperationErrorProto.OpError.EQUIP_NOVICE_ONLY
  }
}
