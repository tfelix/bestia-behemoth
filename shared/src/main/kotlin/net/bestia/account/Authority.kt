package net.bestia.account

enum class Authority {
  KILL,
  MAP_MOVE,
  ITEM,
  EXP,
  SPAWN,

  /**
   * Editing the terrain itself.
   *
   * Separate from [SPAWN] because the two differ in what they put at risk. A spawned mob can be killed; an
   * edited voxel becomes a persistent delta over the generated base, and the base cannot be restored under
   * it once the pipeline has moved on. Worth its own authority even while the only holder is a chat command.
   */
  TERRAIN,

  /** Pushing an arbitrary dialog to your own client, for checking dialog text and placeholders. */
  DIALOG,

  /**
   * Moving the world calendar.
   *
   * Everyone's, not the caller's: there is one clock and it drives the weather, the AI's day/night cycle and
   * every other player's sky. Separate from [TERRAIN] because the two are wrong in different directions - a
   * carve is a local, persistent edit, while this is a global, in-memory one that vanishes on restart.
   */
  WORLD_TIME
}