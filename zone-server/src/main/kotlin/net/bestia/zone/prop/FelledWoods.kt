package net.bestia.zone.prop

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.ground.ColumnKey
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * How many generated trees stand felled, summed per chunk column, so the economy walks a few columns
 * rather than every divergence row. Tick thread only, like the registry it mirrors.
 */
@Service
class FelledWoods(
  divergence: WorldObjectDivergenceRegistry,
  private val kinds: PropKindRegistry,
  private val trees: TreeNeighbourhood,
  private val clock: BestiaClock,
) {

  private class Felled(val column: Long, val weight: Double)

  private val byPropId = HashMap<Long, Felled>()
  private val byColumn = HashMap<Long, Double>()

  init {
    divergence.all().forEach { (propId, entry) -> update(propId, entry) }
    divergence.onChanged(::update)
  }

  fun felledColumns(): List<Long> {
    return byColumn.keys.toList()
  }

  /** Felled trees in [column]. A young tree counts as the share of a grown one it still lacks. */
  fun felledIn(column: Long): Double {
    return byColumn[column] ?: 0.0
  }

  /** Game days a felled tree takes to stand grown again, through stump, sapling and young. */
  fun regrowthDays(): Double {
    val regrowth = kinds.of(StaticEntityKind.TREE).regrowth ?: return 0.0

    return STAGES_TO_GROWN * regrowth.stageSeconds * clock.speedFactor / BESTIA_SECONDS_PER_DAY
  }

  /** How much of [itemId] felling one grown tree yields. */
  fun yieldPerTree(itemId: Long): Double {
    return kinds.of(StaticEntityKind.TREE).loot
      .filter { it.itemId == itemId }
      .sumOf { it.amount * it.dropChance / CERTAIN_DROP }
  }

  private fun update(propId: Long, entry: DivergenceEntry?) {
    byPropId.remove(propId)?.let { old ->
      byColumn.computeIfPresent(old.column) { _, weight -> (weight - old.weight).takeIf { it > ROUNDING } }
    }

    val missing = entry?.let { missingShareOf(it) } ?: 0.0
    if (missing <= 0.0) return

    val column = trees.columnOf(propId).let { ColumnKey.of(it.x, it.y) }
    byPropId[propId] = Felled(column, missing)
    byColumn.merge(column, missing, Double::plus)
  }

  /** Zero for anything but a tree, and for a tree that has grown back. */
  private fun missingShareOf(entry: DivergenceEntry): Double {
    val regrowth = kinds.of(entry.kind).regrowth ?: return 0.0
    val growth = entry.growth ?: return 1.0

    return 1.0 - regrowth.yieldOf(growth)
  }

  private companion object {
    const val STAGES_TO_GROWN = 3
    const val BESTIA_SECONDS_PER_DAY = 24 * 3_600.0
    const val CERTAIN_DROP = 10_000.0

    /** What repeated adding and subtracting leaves of a column that is back to nothing. */
    const val ROUNDING = 1e-9
  }
}
