package net.bestia.zone.account.master

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class SelectMasterCMSG(
  override val playerId: Long,
  val selectedMasterId: EntityId
): CMSG