package net.bestia.zone.quest

import net.bestia.zone.quest.persistence.Quest
import net.bestia.zone.quest.persistence.QuestLogEntry
import java.time.Instant

/** One quest as one master's log shows it. */
data class QuestView(
  val questId: Long,
  val origin: QuestOrigin,
  val status: QuestStatus,
  val role: Role,
  val titleKey: String,
  val textArgs: List<QuestTextArg>,
  val suggestedLevel: Int,
  val deadline: Instant,
  val posterName: String,
  val objectives: List<Objective>,
  val rewards: List<QuestReward>,
) {

  enum class Role {
    ACCEPTED,
    POSTED,
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
  )

  companion object {
    fun accepted(entry: QuestLogEntry): QuestView {
      return of(entry.quest, Role.ACCEPTED, entry.counts)
    }

    fun posted(quest: Quest): QuestView {
      return of(quest, Role.POSTED, counts = emptyList())
    }

    private fun of(quest: Quest, role: Role, counts: List<Int>): QuestView {
      val objectives = quest.objectives.mapIndexed { index, objective ->
        val yours = counts.getOrElse(index) { 0 }
        val progress = if (objective.scope == ObjectiveScope.SHARED) objective.progress else yours
        val placeName = (objective.area as? QuestArea.InPlace)?.placeName
        Objective(
          objective.kind, objective.scope, objective.itemId, objective.speciesId, placeName, objective.required,
          progress, yours,
        )
      }

      return QuestView(
        questId = quest.id,
        origin = quest.origin,
        status = quest.status,
        role = role,
        titleKey = quest.titleKey,
        textArgs = quest.textArgs,
        suggestedLevel = quest.suggestedLevel,
        deadline = quest.deadline,
        posterName = quest.posterName ?: "",
        objectives = objectives,
        rewards = quest.rewards,
      )
    }
  }
}
