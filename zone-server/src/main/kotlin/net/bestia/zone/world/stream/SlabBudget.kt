package net.bestia.zone.world.stream

/** A tick's allowance of expensive slab computations. Mutable on purpose; one instance, shared. */
class SlabBudget(private var remaining: Int) {

  fun trySpend(): Boolean {
    if (remaining <= 0) return false
    remaining--
    return true
  }
}
