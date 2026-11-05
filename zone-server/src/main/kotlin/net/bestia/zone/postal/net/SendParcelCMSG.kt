package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.postal.office.PostOfficeDesk
import net.bestia.zone.util.EntityId

data class SendParcelCMSG(
  override val playerId: Long,
  val postmasterEntityId: EntityId,
  val recipientName: String,
  val lines: List<PostOfficeDesk.Line>,
  val coachOnly: Boolean,
) : CMSG
