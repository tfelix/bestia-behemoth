package net.bestia.zone.quest

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
) {

  val remaining: Int
    get() = required - progress
}
