package net.bestia.zone.quest

import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.text.TextRef

/**
 * One thing a quest asks for. [progress] counts a [ObjectiveScope.SHARED] objective; a personal one counts per master.
 * A master works on the objectives of one [stage] at a time, from 0 up.
 */
data class QuestObjective(
  val kind: ObjectiveKind,
  val scope: ObjectiveScope,
  val required: Int,
  val itemId: Long? = null,
  val speciesId: Long? = null,
  val area: QuestArea? = null,
  val progress: Int = 0,
  val stage: Int = 0,
  /** What an [ObjectiveKind.EXAMINE] or [ObjectiveKind.TALK] objective points at. */
  val target: WorldTarget? = null,
  /** What the master must know for the deed to count: the name it is to tell, the place it is to find. */
  val needsFact: FactRef? = null,
  /** How the log words the objective. A kill or a delivery needs none: the client words those from their ids. */
  val text: TextRef? = null,
  /** What an [ObjectiveKind.DECIDE] objective lets the master pick from. */
  val choices: List<Choice> = emptyList(),
  /** Set when the objective is only for a master who picked this [Choice.id] at an earlier stage. */
  val afterChoice: String? = null,
) {

  data class Choice(val id: String, val text: TextRef)


  val remaining: Int
    get() = required - progress
}
