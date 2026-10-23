package net.bestia.zone.world.stream

/**
 * A tick's allowance of expensive slab computations. Mutable on purpose; one instance, shared.
 *
 * A slab is one 256 m vertical layer of chunks, a chunk's z index. The expensive part is working out which slabs
 * of a column hold a surface ([ChunkService.surfaceSlabsOf], cached per column after that). [ViewVolume] says
 * which slabs are offered.
 */
class SlabBudget(private var remaining: Int) {

  fun trySpend(): Boolean {
    if (remaining <= 0) return false
    remaining--
    return true
  }
}
