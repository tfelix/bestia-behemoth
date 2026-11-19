package net.bestia.zone.questgen.ending

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.QuestService
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.treasure.Relics
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Takes an artifact back into the town that wants it. The quest core only counts: this source takes the goods,
 * as a blackboard does for a delivery. Off the tick.
 */
@Service
class QuestReturns(
  private val plans: StoredQuestPlanRepository,
  private val quests: QuestService,
  private val inventory: InventoryService,
  private val relics: Relics,
  private val chronicle: LivingChronicle,
) {

  /**
   * [relicCopy] leaves the master's bag for good and the history says where the artifact rests now. Counting the
   * step finishes the quest, see [QuestEndings].
   *
   * @return the refusal, or null when the artifact is home.
   */
  @Transactional
  fun handOver(master: LivingChronicle.Doer, planId: Long, relicCopy: Long): OpError? {
    val stored = plans.findLocked(planId)?.takeIf { it.state == StoredQuestPlan.State.RUNNING } ?: return OpError.QUEST_CLOSED
    val questId = stored.questId ?: return OpError.QUEST_CLOSED
    val bring = stored.plan.bringStep ?: return OpError.REQUEST_REFUSED
    val home = stored.plan.broughtTo ?: return OpError.REQUEST_REFUSED
    val artifact = stored.plan.seed.subject

    val isTheArtifact = relics.artifactOf(relicCopy) == artifact.index && inventory.heldInstance(master.masterId, relicCopy) != null
    if (!isTheArtifact || quests.advancePersonal(master.masterId, questId, bring.index, amount = 1) == 0) {
      return OpError.REQUEST_REFUSED
    }

    // Counted, so from here the hand-over happens whole or the transaction rolls back.
    check(inventory.destroyInstance(master.masterId, relicCopy)) { "Relic copy $relicCopy left the bag while it was handed over" }
    chronicle.record(EventKind.ARTIFACT_ENSHRINED, listOf(artifact, Actor(ActorType.SETTLEMENT, home)), by = master)
    afterCommit { relics.forget(listOf(relicCopy)) }

    return null
  }
}
