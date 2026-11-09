package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.StructureCells

/**
 * The player-built cells of a chunk, for the walk tables [ChunkService] builds.
 *
 * Stated here and registered from above through [ChunkService.useStructures], because the structure layer
 * already depends on this one for chunk coordinates.
 */
fun interface StructureCellSource {

  /** Null where nobody built. The answer must be immutable: it may be read on a worker thread. */
  fun cellsOf(chunk: ChunkPos): StructureCells?
}
