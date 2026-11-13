package net.bestia.worldgen.core

/** What ties two figures. Parent and child are not here: a figure's `parent` says that. */
enum class RelationKind {
  SPOUSE,
  RIVAL,
  BETRAYED,

  /** An oath to avenge a slain relative on the people who killed them; `to` is that relative. */
  AVENGER
}
