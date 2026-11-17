package net.bestia.zone.quest.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.QuestLogSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.quest.QuestView

data class QuestLogSMSG(
  val quests: List<QuestView>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setQuestLog(QuestLogSmsgProto.QuestLogSMSG.newBuilder().addAllQuests(quests.map { it.toProto() }))
      .build()
  }
}
