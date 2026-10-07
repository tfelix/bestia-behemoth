package net.bestia.zone.item.container

/**
 * Rows another slice keeps per item instance, deleted before the instance itself. A port, because the slices
 * that keep such rows sit above `item` and may not be named from here.
 */
fun interface ItemInstanceDependents {
  fun deleteFor(instanceIds: Collection<Long>)
}
