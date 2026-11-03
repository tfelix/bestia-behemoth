package net.bestia.zone.honor

/** How precious a stolen item was, which decides what the theft costs. Mirrors the item tiers in pvp.md. */
enum class StolenTier(val act: HonorAct) {
  MUNDANE(HonorAct.STEAL_MUNDANE),
  SUPERIOR(HonorAct.STEAL_SUPERIOR),
  RARE(HonorAct.STEAL_RARE),
  LEGENDARY(HonorAct.STEAL_LEGENDARY),
}
