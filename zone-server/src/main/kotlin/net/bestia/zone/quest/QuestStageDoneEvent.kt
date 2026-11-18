package net.bestia.zone.quest

/**
 * A master met every objective of [stage] of a quest. The quest core only moves the master on; what the last
 * stage is worth is the business of the source that made the quest. Published inside the writing transaction.
 */
data class QuestStageDoneEvent(
  val questId: Long,
  val masterId: Long,
  val stage: Int,
  val wasLast: Boolean,
  /** What the master picked in this stage, when it held a decide objective. */
  val choice: String? = null,
)
