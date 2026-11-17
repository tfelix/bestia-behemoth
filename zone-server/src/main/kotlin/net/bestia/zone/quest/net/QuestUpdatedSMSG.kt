package net.bestia.zone.quest.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.QuestUpdatedSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.quest.QuestView

/** [quest] is null when the quest left the log. */
data class QuestUpdatedSMSG(
  val questId: Long,
  val quest: QuestView?,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val updated = QuestUpdatedSmsgProto.QuestUpdatedSMSG.newBuilder().setQuestId(questId)
    quest?.let { updated.setQuest(it.toProto()) }

    return EnvelopeProto.Envelope.newBuilder()
      .setQuestUpdated(updated)
      .build()
  }
}
