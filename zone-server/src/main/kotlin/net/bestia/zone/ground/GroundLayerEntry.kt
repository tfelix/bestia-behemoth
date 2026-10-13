package net.bestia.zone.ground

/**
 * One layer's cells for one column, in the form they go on the wire.
 *
 * Replaces whatever the client held for [layer]. An entry with no [cells] retires it; see [retired].
 *
 * @property lastDecayedSecond the Bestia second the levels have already faded to, or zero for an encoding that
 *   does not fade
 */
data class GroundLayerEntry(
  val layer: GroundLayer,
  val encoding: GroundLayerEncoding,
  val cells: ByteArray,
  val lastDecayedSecond: Long = 0,
) {

  val isEmpty: Boolean
    get() {
      return cells.isEmpty()
    }

  // `ByteArray` gives data classes reference equality, which would make two identical entries compare unequal.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GroundLayerEntry) return false

    return layer == other.layer &&
        encoding == other.encoding &&
        lastDecayedSecond == other.lastDecayedSecond &&
        cells.contentEquals(other.cells)
  }

  override fun hashCode(): Int {
    var result = layer.hashCode()
    result = 31 * result + encoding.hashCode()
    result = 31 * result + lastDecayedSecond.hashCode()
    result = 31 * result + cells.contentHashCode()
    return result
  }

  override fun toString(): String {
    return "GroundLayerEntry($layer, $encoding, ${cells.size}B, decayed to $lastDecayedSecond)"
  }

  companion object {

    /** Carries the layer's own [encoding] even with nothing in it, so a decoder never meets a layout it refuses. */
    fun retired(layer: GroundLayer, encoding: GroundLayerEncoding): GroundLayerEntry {
      return GroundLayerEntry(layer, encoding, ByteArray(0))
    }
  }
}
