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
  TERRAIN
}