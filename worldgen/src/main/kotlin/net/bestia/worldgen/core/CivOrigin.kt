package net.bestia.worldgen.core

/** How a civilisation came into the world's history. */
enum class CivOrigin {
  /** One of the first peoples, settled at the start. */
  SEEDED,

  /** Landed on an empty coast from over the sea. */
  ARRIVED,

  /** Came from beyond the land's edge and settled somewhere remote. */
  MIGRATED,

  /** Towns that broke away from another civ in a revolt. */
  SECEDED,

  /** Survivors of a town lost to war, ash or blight. */
  REFOUNDED,

  /** The losing house of a succession crisis and its followers. */
  EXILED,

  /** The towns that followed a persecuted cult into revolt. */
  CULT_REALM
}
