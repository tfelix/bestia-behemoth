package net.bestia.zone.quest

import net.bestia.zone.quest.persistence.Quest
import net.bestia.zone.quest.persistence.QuestLogEntry
import java.time.Instant
import net.bestia.zone.text.TextRef

/** One quest as one master's log shows it. */
data class QuestView(
  val questId: Long,
  val origin: QuestOrigin,
  val status: QuestStatus,
  val role: Role,
  val title: TextRef,
  val suggestedLevel: Int,
  val deadline: Instant,
  val posterName: String,
  val objectives: List<Objective>,
  val rewards: List<QuestReward>,
  /** The stage this master works on; 0 for a quest it has not accepted. */
  val stage: Int = 0,
  /** Who took the prize, for a master whose quest was superseded. */
  val wonBy: String? = null,
) {

  enum class Role {
    ACCEPTED,
    POSTED,

    /** On a board, not yet taken by this master. */
    OFFERED,
  }

  /** [yours] is what this master did; for a personal objective it is the whole [progress]. */
  data class Objective(
    val kind: ObjectiveKind,
    val scope: ObjectiveScope,
    val itemId: Long?,
    val speciesId: Long?,
    val placeName: String?,
    val required: Int,
    val progress: Int,
    val yours: Int,
    val stage: Int = 0,
    val text: TextRef? = null,
    val choices: List<QuestObjective.Choice> = emptyList(),
    /** The id this master picked from [choices]; null while it has not. */
    val chosen: String? = null,
  )

  companion object {
    fun accepted(entry: QuestLogEntry): QuestView {
      val view = of(entry.quest, Role.ACCEPTED, entry.counts, entry.stage, entry::isFor, entry.choices)
      if (entry.completedAt != null) {
        // Whatever the quest became for the others later, this master did it.
        return view.copy(status = QuestStatus.COMPLETED)
      }
      val winner = entry.supersededBy ?: return view

      return view.copy(status = QuestStatus.SUPERSEDED, wonBy = winner)
    }

    fun posted(quest: Quest): QuestView {
      return of(quest, Role.POSTED, counts = emptyList())
    }

    fun offered(quest: Quest): QuestView {
      return of(quest, Role.OFFERED, counts = emptyList())
    }

    /** A master sees the stages it reached, so the log cannot be read ahead. The poster sees all of its quest. */
    private fun of(
      quest: Quest,
      role: Role,
      counts: List<Int>,
      stage: Int = 0,
      isForMaster: (QuestObjective) -> Boolean = { true },
      picked: List<String> = emptyList(),
    ): QuestView {
      val lastShownStage = if (role == Role.POSTED) quest.stageCount else stage
      val shown = quest.objectives.withIndex().filter { it.value.stage <= lastShownStage && isForMaster(it.value) }
      val objectives = shown.map { (index, objective) ->
        val yours = counts.getOrElse(index) { 0 }
        val progress = if (objective.scope == ObjectiveScope.SHARED) objective.progress else yours
        val placeName = (objective.area as? QuestArea.InPlace)?.placeName
        Objective(
          objective.kind, objective.scope, objective.itemId, objective.speciesId, placeName, objective.required,
          progress, yours, objective.stage, objective.text, objective.choices,
          chosen = objective.choices.map { it.id }.firstOrNull { it in picked },
        )
      }

      return QuestView(
        questId = quest.id,
        origin = quest.origin,
        status = quest.status,
        role = role,
        title = quest.title,
        suggestedLevel = quest.suggestedLevel,
        deadline = quest.deadline,
        posterName = quest.posterName ?: "",
        objectives = objectives,
        rewards = quest.rewards,
        stage = stage,
      )
    }
  }
}
