package net.bestia.zone.quest.net

import net.bestia.zone.message.CMSG

data class DecideQuestCMSG(
  override val playerId: Long,
  val questId: Long,
  val choiceId: String,
) : CMSG
