package net.bestia.bus.contract

/**
 * What a gatherer takes. [skip] names the categories it leaves alone: LOOSE, PLANT, TREE or CRYSTAL. With
 * [onlyItems], at most 8 item ids, it takes only a prop that yields one of them. Empty, it takes everything.
 */
data class OrderGatherFilter(
  val skip: List<String> = emptyList(),
  val onlyItems: List<Long> = emptyList(),
)
