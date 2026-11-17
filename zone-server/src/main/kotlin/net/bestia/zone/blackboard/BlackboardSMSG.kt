package net.bestia.zone.blackboard

import net.bestia.bnet.proto.BlackboardSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.quest.QuestView
import net.bestia.zone.quest.toProto
import net.bestia.zone.util.EntityId

data class BlackboardSMSG(
  val boardEntityId: EntityId,
  val placeName: String,
  val quests: List<QuestView>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val board = BlackboardSmsgProto.BlackboardSMSG.newBuilder()
      .setBoardEntityId(boardEntityId)
      .setPlaceName(placeName)
      .addAllQuests(quests.map { it.toProto() })

    return EnvelopeProto.Envelope.newBuilder()
      .setBlackboard(board)
      .build()
  }
}
