package net.bestia.zone.postal.net

import net.bestia.zone.message.CMSG

data class SendLetterCMSG(
  override val playerId: Long,
  val recipientName: String,
  val subject: String,
  val body: String,
) : CMSG
