package net.bestia.zone.quest

enum class ObjectiveScope {
  /** Everyone who accepted works towards one counter, kept on the quest. */
  SHARED,

  /** Each master counts on its own, in its log entry. */
  PERSONAL,
}
