package net.bestia.zone.quest

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.ecs.TrackedKill
import net.bestia.zone.quest.persistence.Quest
import net.bestia.zone.quest.persistence.QuestLogEntry
import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The quests sources make and the logs of the masters who took them. Sources decide what a quest asks and when it
 * ends; this keeps the records and announces every change with a [QuestChangedEvent]. Off the tick: every call
 * reads or writes the database.
 */
@Service
class QuestService(
  private val quests: QuestRepository,
  private val entries: QuestLogEntryRepository,
  private val events: ApplicationEventPublisher,
  private val config: QuestConfig,
  private val live: LiveQuests,
  private val clock: Clock = Clock.systemUTC(),
) {

  @Transactional
  fun create(draft: QuestDraft): Quest {
    val quest = quests.save(
      Quest(
        origin = draft.origin,
        titleKey = draft.titleKey,
        textArgs = draft.textArgs,
        suggestedLevel = draft.suggestedLevel,
        deadline = draft.deadline,
        objectives = draft.objectives,
        rewards = draft.rewards,
        posterMasterId = draft.poster?.masterId,
        posterName = draft.poster?.name,
        createdAt = clock.instant(),
      )
    )
    events.publishEvent(QuestChangedEvent(quest.id))

    return quest
  }

  /** @return the refusal, or null when the quest is now in [masterId]'s log. */
  @Transactional
  fun accept(masterId: Long, questId: Long): OpError? {
    val quest = quests.findByIdOrNull(questId) ?: return OpError.QUEST_CLOSED
    if (quest.status != QuestStatus.OPEN) {
      return OpError.QUEST_CLOSED
    }
    if (quest.posterMasterId == masterId || entries.findByMasterIdAndQuestId(masterId, questId) != null) {
      LOG.warn { "Master $masterId tried to accept quest $questId it already holds or posted" }
      return OpError.REQUEST_REFUSED
    }
    if (entries.countByMasterId(masterId) >= config.logCapacity) {
      return OpError.QUEST_LOG_FULL
    }

    val entry = entries.save(QuestLogEntry(masterId, quest, clock.instant(), counts = quest.objectives.map { 0 }))
    events.publishEvent(QuestChangedEvent(questId))
    val kills = trackedKillsOf(entry)
    afterCommit { live.track(masterId, kills) }

    return null
  }

  /**
   * Takes the quest out of [masterId]'s log: an accepted quest is dropped, an ended one it posted is cleared.
   *
   * @return false when there was nothing [masterId] may remove, such as a posted quest still running.
   */
  @Transactional
  fun abandon(masterId: Long, questId: Long): Boolean {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId)
    if (entry != null) {
      entries.delete(entry)
      afterCommit { live.untrack(listOf(masterId), questId) }
      return true
    }

    val posted = quests.findByIdOrNull(questId)?.takeIf { it.posterMasterId == masterId } ?: return false
    if (!posted.status.isEnded) {
      return false
    }
    posted.posterCleared = true

    return true
  }

  /**
   * Counts up to [amount] of [byMasterId]'s work towards shared objective [objectiveIndex], and completes the quest
   * once every shared objective is met. Joins the caller's transaction, so a source pays and counts as one change.
   *
   * @return how much was counted: never more than the objective still needs, and 0 when the quest takes no work.
   */
  @Transactional
  fun advanceShared(questId: Long, objectiveIndex: Int, amount: Int, byMasterId: Long): Int {
    val quest = quests.findLocked(questId) ?: return 0
    val entry = entries.findByMasterIdAndQuestId(byMasterId, questId) ?: return 0
    val objective = quest.objectives[objectiveIndex]
    require(objective.scope == ObjectiveScope.SHARED) { "Objective $objectiveIndex of quest $questId is not shared" }

    val counted = minOf(amount, objective.remaining)
    if (!quest.status.takesWork || counted <= 0) {
      return 0
    }

    quest.objectives = quest.objectives.replaced(objectiveIndex, objective.copy(progress = objective.progress + counted))
    entry.counts = entry.counts.replaced(objectiveIndex, entry.counts[objectiveIndex] + counted)
    if (quest.sharedObjectivesDone) {
      quest.end(QuestStatus.COMPLETED, clock.instant())
    }
    events.publishEvent(QuestChangedEvent(questId))

    return counted
  }

  /** Stops new masters joining; work already under way still counts until the source ends the quest. */
  @Transactional
  fun close(questId: Long) {
    val quest = quests.findLocked(questId) ?: return
    quest.close()
    events.publishEvent(QuestChangedEvent(questId))
  }

  /** Ends the quest unless it already ended, as a completion can race a deadline. */
  @Transactional
  fun end(questId: Long, status: QuestStatus) {
    val quest = quests.findLocked(questId)?.takeUnless { it.status.isEnded } ?: return
    quest.end(status, clock.instant())
    events.publishEvent(QuestChangedEvent(questId))
    val holders = entries.findAllByQuestId(questId).map { it.masterId }
    afterCommit { live.untrack(holders, questId) }
  }

  /**
   * Stores a personal objective's new [count], counted on the tick. Absolute rather than a step, so a job that
   * runs twice or late cannot count a kill twice.
   */
  @Transactional
  fun recordPersonal(masterId: Long, questId: Long, objectiveIndex: Int, count: Int) {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId) ?: return
    if (count <= entry.counts[objectiveIndex]) {
      return
    }
    entry.counts = entry.counts.replaced(objectiveIndex, count)
    events.publishEvent(QuestChangedEvent(questId))
  }

  /** The personal objectives [masterId] counts on the tick while it plays, for its [ActiveQuests][net.bestia.zone.quest.ecs.ActiveQuests]. */
  @Transactional(readOnly = true)
  fun trackedKillsFor(masterId: Long): List<TrackedKill> {
    return entries.findAllByMasterId(masterId).flatMap { trackedKillsOf(it) }
  }

  /** Every quest [masterId] accepted, then every one it posted and has not cleared. */
  @Transactional(readOnly = true)
  fun logOf(masterId: Long): List<QuestView> {
    val accepted = entries.findAllByMasterId(masterId).map { QuestView.accepted(it) }
    val posted = quests.findAllByPosterMasterId(masterId)
      .filterNot { it.posterCleared }
      .map { QuestView.posted(it) }

    return accepted + posted
  }

  /** Every [origin] quest still taking work, as [masterId] sees it on a board: its own, taken, or on offer. */
  @Transactional(readOnly = true)
  fun boardFor(masterId: Long, origin: QuestOrigin): List<QuestView> {
    val taken = entries.findAllByMasterId(masterId).associateBy { it.quest.id }
    val open = quests.findAllByOriginAndStatusIn(origin, QuestStatus.entries.filter { it.takesWork })

    return open.map { quest ->
      val entry = taken[quest.id]
      when {
        entry != null -> QuestView.accepted(entry)
        quest.posterMasterId == masterId -> QuestView.posted(quest)
        else -> QuestView.offered(quest)
      }
    }
  }

  /** The suggested level of every [origin] quest still taking work, for marking where there is work to take. */
  @Transactional(readOnly = true)
  fun openLevels(origin: QuestOrigin): List<Int> {
    return quests.findAllByOriginAndStatusIn(origin, QuestStatus.entries.filter { it.takesWork }).map { it.suggestedLevel }
  }

  /** How [masterId]'s log shows the quest, or null when it is not in that log. */
  @Transactional(readOnly = true)
  fun viewFor(masterId: Long, questId: Long): QuestView? {
    entries.findByMasterIdAndQuestId(masterId, questId)?.let { return QuestView.accepted(it) }

    return quests.findByIdOrNull(questId)
      ?.takeIf { it.posterMasterId == masterId && !it.posterCleared }
      ?.let { QuestView.posted(it) }
  }

  /** The masters whose logs show the quest: everyone who accepted it, and its poster. */
  @Transactional(readOnly = true)
  fun holdersOf(questId: Long): Set<Long> {
    val accepted = entries.findAllByQuestId(questId).map { it.masterId }
    val poster = quests.findByIdOrNull(questId)?.posterMasterId

    return (accepted + listOfNotNull(poster)).toSet()
  }

  @Scheduled(fixedDelay = 60 * 60 * 1000L)
  @Transactional
  fun deleteLongEnded() {
    val endedStatuses = QuestStatus.entries.filter { it.isEnded }
    val stale = quests.findAllByStatusInAndEndedAtBefore(endedStatuses, clock.instant().minus(config.endedKeptFor))
    if (stale.isEmpty()) {
      return
    }

    val ids = stale.map { it.id }
    events.publishEvent(QuestsDeletedEvent(ids))
    entries.deleteAllByQuestIdIn(ids)
    quests.deleteAll(stale)
    LOG.info { "Deleted ${ids.size} quests that ended more than ${config.endedKeptFor} ago" }
  }

  private fun trackedKillsOf(entry: QuestLogEntry): List<TrackedKill> {
    val quest = entry.quest
    if (!quest.status.takesWork) {
      return emptyList()
    }

    return quest.objectives.withIndex()
      .filter { (_, objective) -> objective.kind == ObjectiveKind.KILL && objective.scope == ObjectiveScope.PERSONAL }
      .map { (index, objective) ->
        TrackedKill(
          questId = quest.id,
          objectiveIndex = index,
          speciesId = requireNotNull(objective.speciesId) { "Kill objective $index of quest ${quest.id} names no species" },
          area = objective.area,
          required = objective.required,
          deadline = quest.deadline,
          count = entry.counts[index],
        )
      }
  }

  private fun <T> List<T>.replaced(index: Int, value: T): List<T> {
    return toMutableList().also { it[index] = value }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
