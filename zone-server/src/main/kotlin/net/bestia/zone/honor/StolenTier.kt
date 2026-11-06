package net.bestia.zone.honor

/** How precious a stolen item was, which decides what the theft costs. Mirrors the item tiers in pvp.md. */
enum class StolenTier(val act: HonorAct) {
  MUNDANE(HonorAct.STEAL_MUNDANE),
  SUPERIOR(HonorAct.STEAL_SUPERIOR),
  RARE(HonorAct.STEAL_RARE),
  LEGENDARY(HonorAct.STEAL_LEGENDARY);

  companion object {
    /** The item level bands of items.md. An epic item costs what a legendary one does. */
    fun of(itemLevel: Int): StolenTier {
      return when {
        itemLevel <= 20 -> MUNDANE
        itemLevel <= 50 -> SUPERIOR
        itemLevel <= 80 -> RARE
        else -> LEGENDARY
      }
    }
  }
}
