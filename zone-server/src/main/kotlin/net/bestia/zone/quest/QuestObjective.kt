package net.bestia.zone.quest

/** One thing a quest asks for. [progress] counts a [ObjectiveScope.SHARED] objective; a personal one counts per master. */
data class QuestObjective(
  val kind: ObjectiveKind,
  val scope: ObjectiveScope,
  val required: Int,
  val itemId: Long? = null,
  val speciesId: Long? = null,
  val area: QuestArea? = null,
  val progress: Int = 0,
) {

  val remaining: Int
    get() = required - progress
}
