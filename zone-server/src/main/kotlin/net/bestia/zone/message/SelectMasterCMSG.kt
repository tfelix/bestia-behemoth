package net.bestia.zone.message

import net.bestia.zone.util.EntityId

data class SelectMasterCMSG(
  override val playerId: Long,
  val selectedMasterId: EntityId
): CMSG