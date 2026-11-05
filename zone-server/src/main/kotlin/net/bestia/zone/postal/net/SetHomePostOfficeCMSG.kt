package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class SetHomePostOfficeCMSG(
  override val playerId: Long,
  val postmasterEntityId: EntityId,
) : CMSG
