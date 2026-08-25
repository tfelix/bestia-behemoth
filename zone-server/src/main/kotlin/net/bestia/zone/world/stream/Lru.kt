package net.bestia.zone.world.stream

/**
 * A bounded, access-ordered map: the least recently *read* entry is the one that leaves.
 *
 * Access order rather than insertion order, because every cache in this package is warmed by players
 * revisiting the same ground - a chunk nobody has looked at in a while is the right one to drop, and the
 * chunk that arrived first is not.
 */
internal class Lru<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
}
