package net.bestia.worldgen.core

/** A lasting tie between two figures, and the event that made it. */
data class RelationRecord(
  val kind: RelationKind,
  /** Figure index. For [RelationKind.BETRAYED], the traitor. */
  val from: Int,
  /** Figure index. For [RelationKind.BETRAYED], the ruler they betrayed. */
  val to: Int,
  val since: Int
)
