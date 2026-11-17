package net.bestia.zone.quest.net

import net.bestia.zone.message.CMSG

data class AbandonQuestCMSG(
  override val playerId: Long,
  val questId: Long,
) : CMSG
