package net.bestia.zone.ecs.spawn.ambient

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.math.hypot

/**
 * Every settlement history left standing, indexed for the two questions the ambient layer asks of towns.
 *
 * Both are cheap and neither is the same question `AreaNameRegistry` answers - that one resolves a position
 * to the name of the place it is in, and stops at the footprint edge. These reach past it:
 *
 *  - **How far is the nearest town?** The largest term in `SpawnDangerCurve`, and therefore what makes the
 *    country around a settlement gentle and the deep wilderness harsh. Without it the curve would rate a
 *    village's fields exactly as it rates identical grassland forty kilometres out.
 *  - **Which towns could this position be inside?** The broad phase of [TownClearance], which only reaches
 *    for a town's real outline once a cheap disc test says it might matter.
 *
 * A bucket lattice rather than the feature index, for `SpawnerCellIndex`'s reason: there are on the order of
 * a thousand settlements and they never move, so a hash probe over a table built once at boot beats a
 * spatial query that has to consider the tens of thousands of dens and the buildings sharing that index.
 */
class StandingSettlements private constructor(
  private val entries: List<Entry>,
  private val byCell: Map<Long, List<Entry>>
) {

  private val byIndex: Map<Int, Entry> = entries.associateBy { it.index }

  /** A standing settlement's centre and the radius of the ground it graded. */
  class Entry(val index: Int, val x: Double, val y: Double, val tier: SettlementTier) {
    val footprintRadius: Double get() = tier.footprintRadius
  }

  val size: Int get() = entries.size

  /** The settlement history left standing at [index], or null for one it never founded or left a ruin. */
  fun entryOf(index: Int): Entry? {
    return byIndex[index]
  }

  /**
   * Metres to the nearest standing settlement centre, or [Double.MAX_VALUE] on a world with none.
   *
   * Searched by widening rings of lattice cells and stopping as soon as the best hit so far cannot be beaten
   * by anything further out. Towns are sparse - a city keeps 55 km from the next - so on most of the map
   * this reads several empty rings and then one entry.
   */
  fun nearestDistance(x: Double, y: Double): Double {
    if (entries.isEmpty()) return Double.MAX_VALUE

    val centreX = Math.floorDiv(x.toLong(), CELL_METRES)
    val centreY = Math.floorDiv(y.toLong(), CELL_METRES)

    var best = Double.MAX_VALUE
    var ring = 0L
    while (true) {
      forEachCellInRing(centreX, centreY, ring) { cell ->
        for (entry in byCell[cell].orEmpty()) {
          val distance = hypot(entry.x - x, entry.y - y)
          if (distance < best) best = distance
        }
      }

      // Anything in a further ring is at least this far away, so a hit already inside that bound is final.
      val guaranteed = (ring * CELL_METRES).toDouble()
      if (best <= guaranteed) return best

      ring++
      if (ring > MAX_RINGS) return best
    }
  }

  /** Settlements whose footprint, widened by [margin] metres, contains ([x], [y]). */
  fun coveringWithin(x: Double, y: Double, margin: Double): List<Entry> {
    val reachCells = ((MAX_FOOTPRINT_RADIUS + margin) / CELL_METRES).toLong() + 1
    val centreX = Math.floorDiv(x.toLong(), CELL_METRES)
    val centreY = Math.floorDiv(y.toLong(), CELL_METRES)

    var found: MutableList<Entry>? = null
    for (cy in (centreY - reachCells)..(centreY + reachCells)) {
      for (cx in (centreX - reachCells)..(centreX + reachCells)) {
        for (entry in byCell[pack(cx, cy)].orEmpty()) {
          if (hypot(entry.x - x, entry.y - y) <= entry.footprintRadius + margin) {
            (found ?: ArrayList<Entry>(2).also { found = it }).add(entry)
          }
        }
      }
    }
    return found ?: emptyList()
  }

  private inline fun forEachCellInRing(centreX: Long, centreY: Long, ring: Long, action: (Long) -> Unit) {
    if (ring == 0L) {
      action(pack(centreX, centreY))
      return
    }

    for (d in -ring..ring) {
      action(pack(centreX + d, centreY - ring))
      action(pack(centreX + d, centreY + ring))
    }
    // The corners belong to the horizontal runs above, so the vertical ones stop one short of them.
    for (d in (-ring + 1)..(ring - 1)) {
      action(pack(centreX - ring, centreY + d))
      action(pack(centreX + ring, centreY + d))
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Edge of one index cell, in metres. Positional, so it is a `const`: changing it reinterprets every key.
     */
    private const val CELL_METRES = 4_000L

    /** Largest [SettlementTier.footprintRadius], so [coveringWithin] knows how far a town can reach. */
    private val MAX_FOOTPRINT_RADIUS = SettlementTier.entries.maxOf { it.footprintRadius }

    /**
     * Ceiling on the widening search, so a world with one settlement in a corner cannot walk the whole
     * lattice for every query. At 4 km cells this is a 400 km reach, past any world we generate.
     */
    private const val MAX_RINGS = 100L

    /**
     * Reads the standing settlements off the world.
     *
     * The same join `AreaNameRegistry.loadSettlements` and `SettlementSpawnPoints` perform: placement knows
     * where and what tier, history knows whether anybody ever lived there. A site never founded, or left a
     * ruin, exerts no calming influence on the wilderness and must not shrink a keep-out ring either.
     */
    fun of(generated: GeneratedWorld): StandingSettlements {
      val chronicle = generated.world.chronicle
      val entries = ArrayList<Entry>()

      for (feature in generated.world.features.all()) {
        if (feature !is PointMarker || feature.kind != FeatureKind.SETTLEMENT) continue

        val index = feature.attribute(SettlementChannels.INDEX).toInt()
        val record = chronicle.settlements.getOrNull(index) ?: continue
        if (!record.wasFounded || record.isRuin) continue

        val tier = SettlementTier.entries[feature.attribute(SettlementChannels.TIER).toInt()]
        entries.add(Entry(index, feature.position.x, feature.position.y, tier))
      }

      val byCell = HashMap<Long, MutableList<Entry>>()
      for (entry in entries) {
        val cell = pack(
          Math.floorDiv(entry.x.toLong(), CELL_METRES),
          Math.floorDiv(entry.y.toLong(), CELL_METRES)
        )
        byCell.getOrPut(cell) { ArrayList() }.add(entry)
      }

      LOG.info { "Ambient spawn: indexed ${entries.size} standing settlement(s)" }

      return StandingSettlements(entries, byCell)
    }

    private fun pack(cellX: Long, cellY: Long): Long {
      return (cellX shl 32) or (cellY and 0xFFFFFFFFL)
    }
  }
}
