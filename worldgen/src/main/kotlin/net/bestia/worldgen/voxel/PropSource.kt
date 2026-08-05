package net.bestia.worldgen.voxel

/**
 * A kind of positioned object the chunk tier emits instead of writing into the voxel grid.
 *
 * The ordinal is part of [PropId], so **the order of these is a wire and storage contract**: a runtime
 * keys stored per-prop state on the packed identity, and renumbering renames every prop in the world.
 * Append only.
 */
enum class PropKind {

  /** A tree. Its trunk stands in exactly one voxel column; [PropInstances.radiusAt] is the crown. */
  TREE,

  /** A mana crystal grown from the ambient mana field. */
  MANA_CRYSTAL,

  /**
   * A crystal spire in the blast field of a `WOUND`.
   *
   * A separate kind from [MANA_CRYSTAL] rather than a flag on one, because it sits on a **different
   * lattice** with a different spacing - so the two share a cell index space, and one identity would
   * name two different objects.
   */
  WOUND_SPIRE,

  /**
   * Aetherite pushed up over a corrupted orebody: the surface sign that there is aetherite below.
   *
   * The one prop that is a **prospecting hint rather than a thing in its own right**. Placer gold is the
   * precedent - `ResourceStage.tracePlacers` puts gold in the gravels downstream of a lode so that a player
   * who finds it can walk upstream to the lode - and this is the vertical version of the same idea: shards on
   * the ground mean the rock under them yields aetherite, and `OreVeins` will agree, because both read the
   * same corruption threshold on the same deposit.
   *
   * Not a flag on [MANA_CRYSTAL], and the distinction is not merely the lattice this time. A mana crystal is
   * grown *by* the ambient mana field and says only that the mana here is high; this is a mineral that says
   * something specific and diggable about one body of rock. Sharing a kind would make the two
   * indistinguishable to a runtime deciding what harvesting one yields.
   */
  AETHERITE_SHARD,

  /**
   * A point of interest: one of the hand-authored landmarks `poi/PoiStage` rolled onto this world.
   *
   * **One kind for the whole catalogue, and the specific landmark is [PropInstances.subKindAt].** The reason
   * is arithmetic: [PropId] has four bits of kind, so a catalogue with a dozen entries in it would eat the
   * whole space and cap itself at twelve forever. `PoiKind`'s ordinal is a byte on the instance instead, which
   * is what lets the catalogue grow to as many landmarks as there is art for.
   *
   * The one kind that is **not a scatter**. Every other kind here is a function of position that a chunk can
   * evaluate for itself; this one comes from a `FeatureKind.POI` marker the world tier placed, because "one of
   * these somewhere in the world" is not a question a lattice can answer. See `PoiProps`.
   */
  POI
}

/**
 * Ground elevation under a prop, or [Double.NaN] where nothing may stand there.
 *
 * The one thing a scatter cannot work out for itself. It knows the climate, the soil, the mana and
 * the water; it does not know that somebody paved this spot, built a granary on it, ran a bridge over
 * it or opened a cave mouth under it. Those are questions about the *other* producers in the chunk
 * tier, and they are asked at the prop's own position rather than at any column near it.
 *
 * The elevation must come from the same `ColumnHeights` the terrain is built from and never from the
 * base heightfield: every vector feature that moves the ground has already been stamped into it, so
 * sampling the base instead floats or buries props wherever the world was graded.
 */
fun interface PropSite {
  fun groundAt(worldX: Double, worldY: Double): Double
}

/** Bit flags on an emitted prop. */
object PropFlags {

  /** Standing on corrupted ground: the blighted variant of whatever this kind looks like. */
  const val BLIGHTED = 1

  /** The larger of a kind's two size variants, where it has them. */
  const val LARGE = 2
}

/**
 * The durable name of a prop: its kind and its lattice cell, packed into a long.
 *
 * ### Why the cell and not a hash
 *
 * A runtime stores per-prop divergence - felled, burnt, regrowing - against this. A 64-bit hash of the
 * cell would collide silently somewhere among the millions of props in a world, and a collision means
 * two trees sharing one row of state with no way to notice. The cell index is injective by
 * construction.
 *
 * ### What breaks it
 *
 * The identity is stable only while the **lattice spacing** is. A cell index is a position
 * quantisation, so changing `VegetationParams.cellSize` or `CrystalParams.cellSize` renames every prop
 * in the world and orphans every stored row - the same cost `history/Names.kt` states for name seeds,
 * and the same conclusion: not a thing to change after a world ships. Thinning knobs are safe by
 * contrast, because they change *which cells* emit rather than what a cell is called.
 *
 * Four bits of kind and thirty of each axis. Thirty signed bits at a four-metre cell reaches two
 * million kilometres, against a design world of four thousand.
 */
object PropId {

  private const val AXIS_BITS = 30
  private const val AXIS_MASK = (1L shl AXIS_BITS) - 1
  private const val AXIS_LIMIT = 1L shl (AXIS_BITS - 1)
  private const val KIND_SHIFT = AXIS_BITS * 2
  private const val KIND_CAPACITY = 1 shl 4

  init {
    require(PropKind.entries.size <= KIND_CAPACITY) {
      "PropId has four bits of kind; ${PropKind.entries.size} kinds do not fit"
    }
  }

  fun of(kind: PropKind, cellX: Long, cellY: Long): Long {
    require(cellX in -AXIS_LIMIT until AXIS_LIMIT) { "lattice cell x $cellX does not fit $AXIS_BITS bits" }
    require(cellY in -AXIS_LIMIT until AXIS_LIMIT) { "lattice cell y $cellY does not fit $AXIS_BITS bits" }

    return (kind.ordinal.toLong() shl KIND_SHIFT) or
        ((cellX and AXIS_MASK) shl AXIS_BITS) or
        (cellY and AXIS_MASK)
  }

  fun kindOf(id: Long): PropKind = PropKind.entries[(id ushr KIND_SHIFT).toInt()]

  fun cellXOf(id: Long): Long = signed((id ushr AXIS_BITS) and AXIS_MASK)

  fun cellYOf(id: Long): Long = signed(id and AXIS_MASK)

  private fun signed(value: Long): Long =
    if (value >= AXIS_LIMIT) value - (1L shl AXIS_BITS) else value
}

/**
 * The props whose own position falls inside one chunk.
 *
 * ### A struct of arrays, and a compact one
 *
 * The module's house style for a chunk-shaped answer - `SurfaceColumns`, `ColumnHeights`,
 * `WalkableTile` - and here it is also what makes a whole-world sweep possible: an invariant scanning
 * a world visits millions of props, and a `data class` each would be millions of allocations.
 *
 * Compact rather than one slot per lattice cell. `VegetationScatter.Candidates` was indexed by cell
 * with `Double.NaN` marking the empty ones, because a column filling itself needed O(1) `indexOf` to
 * ask "which trees reach me". No consumer asks that any more - a prop is a point, not a crown spanning
 * sixteen columns - so the sentinel, the cell-range fields and nine tenths of the memory go with it.
 *
 * ### Ownership is exact
 *
 * A prop appears in the instances of **exactly one** chunk, decided by the integer voxel column its
 * position falls in rather than by a bounds test. `vector/Aabb.contains` is a closed interval, so a
 * trunk exactly on a chunk boundary would be claimed by both of the chunks that share it.
 */
class PropInstances(initialCapacity: Int = 32) {

  private var x = DoubleArray(initialCapacity)
  private var y = DoubleArray(initialCapacity)
  private var ground = DoubleArray(initialCapacity)
  private var identity = LongArray(initialCapacity)
  private var kind = ByteArray(initialCapacity)
  private var subKind = ByteArray(initialCapacity)
  private var heightM = FloatArray(initialCapacity)
  private var radiusM = FloatArray(initialCapacity)
  private var flags = ByteArray(initialCapacity)

  var count: Int = 0
    private set

  val isEmpty get() = count == 0
  val indices get() = 0 until count

  /** World X of the prop's own position, in metres. */
  fun xAt(i: Int) = x[i]

  /** World Y of the prop's own position, in metres. */
  fun yAt(i: Int) = y[i]

  /**
   * Ground elevation under the prop, in metres.
   *
   * Resolved by the producer from the same `ColumnHeights` the terrain is built from, **not** from the
   * base heightfield: every vector feature that moves the ground - a road, a river bank, a settlement
   * grading - has already been stamped into it. A consumer sampling the base instead would float or
   * bury props wherever the world was graded.
   */
  fun groundAt(i: Int) = ground[i]

  /** The prop's durable name. See [PropId]. */
  fun identityAt(i: Int) = identity[i]

  fun kindAt(i: Int): PropKind = PropKind.entries[kind[i].toInt()]

  /**
   * Which sub-type of its kind this prop is, or zero for a kind that has only one form.
   *
   * Today that means the `PoiKind` ordinal on a [PropKind.POI], and nothing else uses it - see
   * [PropKind.POI] for why the catalogue rides here rather than taking a kind each.
   *
   * Deliberately **not** called `variant`: the runtime already uses that word for "which of a kind's N
   * meshes", rolled off the prop's identity in `GeneratedPropSource.variantOf`, and one word for two
   * different numbers in adjacent code is how the two get conflated. This one says *what the thing is*; that
   * one says which of several models of the same thing to draw.
   */
  fun subKindAt(i: Int) = subKind[i].toInt()

  /** Trunk height for a tree, overall height for a crystal or spire, in metres. */
  fun heightAt(i: Int) = heightM[i].toDouble()

  /** Crown radius for a tree, in metres. Zero for a kind that has no spread. */
  fun radiusAt(i: Int) = radiusM[i].toDouble()

  fun isBlighted(i: Int) = flags[i].toInt() and PropFlags.BLIGHTED != 0

  fun isLarge(i: Int) = flags[i].toInt() and PropFlags.LARGE != 0

  fun add(
    kind: PropKind,
    identity: Long,
    x: Double,
    y: Double,
    ground: Double,
    heightM: Double,
    radiusM: Double = 0.0,
    flags: Int = 0,
    subKind: Int = 0
  ) {
    require(subKind in 0..Byte.MAX_VALUE) { "subKind $subKind does not fit a byte" }

    ensureCapacity(count + 1)

    this.kind[count] = kind.ordinal.toByte()
    this.subKind[count] = subKind.toByte()
    this.identity[count] = identity
    this.x[count] = x
    this.y[count] = y
    this.ground[count] = ground
    this.heightM[count] = heightM.toFloat()
    this.radiusM[count] = radiusM.toFloat()
    this.flags[count] = flags.toByte()

    count++
  }

  private fun ensureCapacity(required: Int) {
    if (required <= x.size) return

    var size = x.size
    while (size < required) size *= 2

    x = x.copyOf(size)
    y = y.copyOf(size)
    ground = ground.copyOf(size)
    identity = identity.copyOf(size)
    kind = kind.copyOf(size)
    subKind = subKind.copyOf(size)
    heightM = heightM.copyOf(size)
    radiusM = radiusM.copyOf(size)
    flags = flags.copyOf(size)
  }
}
