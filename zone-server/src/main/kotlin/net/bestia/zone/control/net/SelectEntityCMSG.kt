package net.bestia.zone.control.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class SelectEntityCMSG(
  override val playerId: Long,
  val entityId: EntityId
): CMSG