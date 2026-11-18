package net.bestia.zone.quest

/** Who made a quest. Quests are made at runtime, so this names a maker, never a list of quests. */
enum class QuestOrigin {
  BLACKBOARD,

  /** The world itself, through the QuestManager. */
  WORLD,
}
