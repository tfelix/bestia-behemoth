package net.bestia.zone.world.ground

/**
 * Names one chunk column: the 32 m footprint every ground mark, scar and wear grid is stored against.
 *
 * A packed `Long` rather than a pair, because it is a `HashMap` key on the tick thread and a primary key in
 * four tables. The packing itself is not interesting; having exactly one of it is, which is why this exists
 * instead of each subsystem shifting its own chunk coordinates together and eventually disagreeing about the
 * sign of a negative one.
 */
object ColumnKey {

  fun of(chunkX: Int, chunkY: Int): Long {
    return (chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)
  }

  fun chunkXOf(columnKey: Long): Int {
    return (columnKey shr 32).toInt()
  }

  fun chunkYOf(columnKey: Long): Int {
    return columnKey.toInt()
  }
}
