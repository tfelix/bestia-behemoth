package net.bestia.worldgen.core

/** One open story of the history, and the event it has stayed open since. See [ThreadKind] for the actors. */
data class ThreadRecord(
  val kind: ThreadKind,
  val actors: List<Actor>,
  /** Event id. Pruning keeps it, so a thread never points at a dropped event. */
  val since: Int
)
