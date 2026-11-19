package net.bestia.zone.quest

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.quest.ecs.TrackedDeed
import net.bestia.zone.quest.ecs.TrackedKill
import net.bestia.zone.quest.ecs.TrackedObjectives
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
import java.time.Duration

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
        title = draft.title,
        suggestedLevel = draft.suggestedLevel,
        deadline = draft.deadline,
        objectives = draft.objectives,
        rewards = draft.rewards,
        posterMasterId = draft.poster?.masterId,
        posterName = draft.poster?.name ?: draft.issuer,
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
    val tracked = trackedOf(entry)
    afterCommit { live.track(masterId, tracked) }

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

  /**
   * The one prize of the quest is taken. It is over for every master but [winnerMasterId], who goes on with what
   * is left to do. When the winner is not in the quest, it ends for everyone.
   */
  @Transactional
  fun supersede(questId: Long, winnerMasterId: Long?, winnerName: String) {
    val quest = quests.findLocked(questId) ?: return
    val holders = entries.findAllByQuestId(questId)
    val losers = holders.filter { it.masterId != winnerMasterId && it.supersededBy == null }
    losers.forEach { it.supersededBy = winnerName }
    if (holders.none { it.masterId == winnerMasterId } && !quest.status.isEnded) {
      quest.end(QuestStatus.SUPERSEDED, clock.instant())
    }

    events.publishEvent(QuestChangedEvent(questId))
    val loserIds = losers.map { it.masterId }
    afterCommit { live.untrack(loserIds, questId) }
  }

  /**
   * [masterId] is done with a quest that stays open: others may still take it and finish it. For a quest many
   * masters do each for themselves.
   */
  @Transactional
  fun completeFor(masterId: Long, questId: Long) {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId)?.takeIf { it.takesWork } ?: return
    entry.completedAt = clock.instant()
    events.publishEvent(QuestChangedEvent(questId))
    afterCommit { live.untrack(listOf(masterId), questId) }
  }

  /** Moves the deadline to [within] from now, unless it is closer already. */
  @Transactional
  fun endNoLaterThan(questId: Long, within: Duration) {
    val quest = quests.findLocked(questId) ?: return
    quest.endNoLaterThan(clock.instant().plus(within))
    events.publishEvent(QuestChangedEvent(questId))
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

  /** Ends a quest that still takes work past its deadline. @return true when this call ended it. */
  @Transactional
  fun expireIfOverdue(questId: Long): Boolean {
    val overdue = quests.findByIdOrNull(questId)?.let { it.status.takesWork && clock.instant().isAfter(it.deadline) } ?: false
    if (overdue) {
      end(questId, QuestStatus.EXPIRED)
    }

    return overdue
  }

  /**
   * Stores a personal objective's new [count], counted on the tick. Absolute rather than a step, so a job that
   * runs twice or late cannot count a kill twice.
   */
  @Transactional
  fun recordPersonal(masterId: Long, questId: Long, objectiveIndex: Int, count: Int) {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId)?.takeIf { it.takesWork } ?: return
    if (count <= entry.counts[objectiveIndex]) {
      return
    }
    entry.counts = entry.counts.replaced(objectiveIndex, count)
    moveOnIfStageMet(entry)
    events.publishEvent(QuestChangedEvent(questId))
  }

  /**
   * Counts up to [amount] towards personal objective [objectiveIndex], for work a source takes off the tick, such
   * as goods handed over. Joins the caller's transaction, so a source takes and counts as one change.
   *
   * @return how much was counted: never more than the objective still needs, and 0 when it takes no work now.
   */
  @Transactional
  fun advancePersonal(masterId: Long, questId: Long, objectiveIndex: Int, amount: Int): Int {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId) ?: return 0
    val objective = entry.quest.objectives[objectiveIndex]
    require(objective.scope == ObjectiveScope.PERSONAL) { "Objective $objectiveIndex of quest $questId is not personal" }

    val takesWork = entry.takesWork && objective.stage == entry.stage && entry.isFor(objective)
    val counted = minOf(amount, objective.required - entry.counts[objectiveIndex])
    if (!takesWork || counted <= 0) {
      return 0
    }

    entry.counts = entry.counts.replaced(objectiveIndex, entry.counts[objectiveIndex] + counted)
    moveOnIfStageMet(entry)
    events.publishEvent(QuestChangedEvent(questId))

    return counted
  }

  /**
   * Picks [choiceId] at the decide objective of the stage [masterId] works on. A pick is final.
   *
   * @return the refusal, or null when the pick was taken.
   */
  @Transactional
  fun decide(masterId: Long, questId: Long, choiceId: String): OpError? {
    val entry = entries.findByMasterIdAndQuestId(masterId, questId) ?: return OpError.REQUEST_REFUSED
    val quest = entry.quest
    if (!entry.takesWork) {
      return OpError.QUEST_CLOSED
    }
    val open = quest.objectives.withIndex().firstOrNull { (index, objective) ->
      objective.kind == ObjectiveKind.DECIDE && objective.stage == entry.stage && entry.isFor(objective) && entry.counts[index] == 0
    }
    if (open == null || open.value.choices.none { it.id == choiceId }) {
      LOG.warn { "Master $masterId picked '$choiceId' for quest $questId, which offers no such choice now" }
      return OpError.REQUEST_REFUSED
    }

    entry.counts = entry.counts.replaced(open.index, 1)
    entry.choices = entry.choices + choiceId
    moveOnIfStageMet(entry, choiceId)
    events.publishEvent(QuestChangedEvent(questId))

    return null
  }

  private fun moveOnIfStageMet(entry: QuestLogEntry, choice: String? = null) {
    val quest = entry.quest
    if (entry.stage >= quest.stageCount || !entry.stageMet) {
      return
    }

    val done = entry.stage
    entry.stage = entry.stageAfter(done)
    events.publishEvent(
      QuestStageDoneEvent(quest.id, entry.masterId, done, wasLast = entry.stage == quest.stageCount, choice = choice)
    )
    val tracked = trackedOf(entry)
    afterCommit {
      live.untrack(listOf(entry.masterId), quest.id)
      live.track(entry.masterId, tracked)
    }
  }

  /** The personal objectives [masterId] counts on the tick while it plays, for its [ActiveQuests][net.bestia.zone.quest.ecs.ActiveQuests]. */
  @Transactional(readOnly = true)
  fun trackedFor(masterId: Long): TrackedObjectives {
    val tracked = entries.findAllByMasterId(masterId).map { trackedOf(it) }

    return TrackedObjectives(tracked.flatMap { it.kills }, tracked.flatMap { it.deeds })
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

  /** What the master counts in the stage it works on. */
  private fun trackedOf(entry: QuestLogEntry): TrackedObjectives {
    val quest = entry.quest
    if (!entry.takesWork) {
      return TrackedObjectives()
    }

    val current = quest.objectives.withIndex()
      .filter { (_, objective) -> objective.stage == entry.stage && objective.scope == ObjectiveScope.PERSONAL }
      .filter { (_, objective) -> entry.isFor(objective) }
    val kills = current.filter { it.value.kind == ObjectiveKind.KILL }.map { (index, objective) ->
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
    val deeds = current.mapNotNull { (index, objective) ->
      val deed = DEED_OF[objective.kind] ?: return@mapNotNull null
      TrackedDeed(
        questId = quest.id,
        objectiveIndex = index,
        deed = deed,
        target = requireNotNull(objective.target) { "Objective $index of quest ${quest.id} names no target" },
        needsFact = objective.needsFact,
        required = objective.required,
        deadline = quest.deadline,
        count = entry.counts[index],
      )
    }

    return TrackedObjectives(kills, deeds)
  }

  private fun <T> List<T>.replaced(index: Int, value: T): List<T> {
    return toMutableList().also { it[index] = value }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    val DEED_OF = mapOf(
      ObjectiveKind.EXAMINE to DeedFact.Deed.EXAMINED,
      ObjectiveKind.TALK to DeedFact.Deed.TALKED_TO,
    )
  }
}
