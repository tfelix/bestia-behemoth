package net.bestia.zone.faction.novitiate

import net.bestia.zone.faction.persistence.NovitiateRecord
import net.bestia.zone.faction.persistence.NovitiateRecordRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.faction.FactionService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStageDoneEvent
import net.bestia.zone.quest.QuestStatus
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** Ends a Novitiate at its oath: a master who swears joins the Order, one who withdraws stays unsworn. */
@Component
class NovitiateOath(
  private val records: NovitiateRecordRepository,
  private val quests: QuestService,
  private val masters: MasterRepository,
  private val factions: FactionService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) {

  @EventListener
  fun onStageDone(event: QuestStageDoneEvent) {
    if (!event.wasLast) {
      return
    }
    val record = records.findByQuestId(event.questId) ?: return

    records.delete(record)
    quests.end(event.questId, QuestStatus.COMPLETED)
    if (event.choice != NovitiateQuest.SWEAR) {
      return
    }

    val master = masters.findByIdOrNull(event.masterId) ?: return
    val accountId = master.account.id
    afterCommit { world.post { swear(this, master.entityId, accountId, record) } }
  }

  /** Asked again on the tick: the master may have changed since the quest was given, by a GM or by levels. */
  private fun swear(world: World, masterEntityId: Long, accountId: Long, record: NovitiateRecord) {
    val refusal = factions.swear(world, masterEntityId, record.faction) ?: run {
      LOG.info { "Master ${record.masterId} swore to ${record.faction}" }
      return
    }

    val code = when (refusal) {
      FactionService.Refusal.ALREADY_SWORN -> OpError.FACTION_SWORN_ELSEWHERE
      FactionService.Refusal.STILL_UNSWORN -> OpError.FACTION_STILL_UNSWORN
      FactionService.Refusal.LEVEL_TOO_LOW -> OpError.FACTION_LEVEL_TOO_LOW
    }
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
