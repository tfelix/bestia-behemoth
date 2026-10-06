package net.bestia.zone.spawn.ecs

/** Hears of every mob that [DeathSystem] removes. The towns implement it to decide what is news. */
interface KillReporter {

  /**
   * Takes the species id and a position rather than an entity: by the time this runs the entity is
   * being destroyed, and a caller that handed over a live reference would be racing its own tick.
   */
  fun report(speciesId: Long, voxelX: Long, voxelY: Long)
}
