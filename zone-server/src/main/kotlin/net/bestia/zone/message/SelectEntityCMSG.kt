package net.bestia.zone.message

import net.bestia.zone.util.EntityId

data class SelectEntityCMSG(
  override val playerId: Long,
  val entityId: EntityId
): CMSG

