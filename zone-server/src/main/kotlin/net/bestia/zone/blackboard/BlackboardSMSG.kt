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
  val terms: Terms,
  val notices: List<EventNotices.Notice> = emptyList(),
) : SMSG {

  /** What the poster may pick, so the client prices a quest the way the board will. */
  data class Terms(
    val feePercentByDays: Map<Int, Int>,
    val openPostings: Int,
    val maxPostings: Int,
    val maxAmount: Int,
    val maxCoinsEach: Long,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val board = BlackboardSmsgProto.BlackboardSMSG.newBuilder()
      .setBoardEntityId(boardEntityId)
      .setPlaceName(placeName)
      .addAllQuests(quests.map { it.toProto() })
      .setOpenPostings(terms.openPostings)
      .setMaxPostings(terms.maxPostings)
      .setMaxAmount(terms.maxAmount)
      .setMaxCoinsEach(terms.maxCoinsEach)
    terms.feePercentByDays.toSortedMap().forEach { (days, percent) ->
      board.addRuns(BlackboardSmsgProto.BlackboardRun.newBuilder().setDays(days).setFeePercent(percent))
    }
    notices.forEach { board.addNotices(it.toProto()) }

    return EnvelopeProto.Envelope.newBuilder()
      .setBlackboard(board)
      .build()
  }

  private fun EventNotices.Notice.toProto(): BlackboardSmsgProto.BlackboardNotice {
    return BlackboardSmsgProto.BlackboardNotice.newBuilder()
      .setEventId(eventId)
      .setPlaceName(placeName)
      .setX(x)
      .setY(y)
      .setStage(stage)
      .build()
  }
}
