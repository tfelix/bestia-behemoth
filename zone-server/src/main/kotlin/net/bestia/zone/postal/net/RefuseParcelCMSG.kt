package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class RefuseParcelCMSG(
  override val playerId: Long,
  val postmasterEntityId: EntityId,
  val parcelId: Long,
) : CMSG
