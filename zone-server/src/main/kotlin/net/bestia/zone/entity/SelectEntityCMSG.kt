package net.bestia.zone.entity

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class SelectEntityCMSG(
  override val playerId: Long,
  val entityId: EntityId
): CMSG