package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaField.Companion.TILE_CELLS
import org.springframework.stereotype.Component

/**
 * The tier of each field cell, with hysteresis and a dwell time so a place does not flicker between two tiers.
 *
 * A cell only changes tier once its density has stayed past the border for [ManaTierRule.dwellSeconds], and it
 * falls only once the density is [ManaTierRule.HYSTERESIS] below the border. Only cells whose tier differs from
 * their start density's keep that memory; every other cell sits at the tier of its start density.
 *
 * Tick-thread only, like [ManaField].
 */
@Component
class ManaTiers(
  private val field: ManaField,
  private val ground: ManaFieldRule.Ground,
  private val listeners: List<ManaTierListener>,
) {

  private class Memory(var tier: ManaTier, var pending: ManaTier?, var pendingSince: Double)

  private val memory = HashMap<Cell, Memory>()

  private var now = 0.0

  /** False until the first update, which takes the field as it was saved instead of watching it rise. */
  private var primed = false

  fun tierAt(at: Vec3L): ManaTier = tierOf(Cell(ManaField.cellOf(at.x.toDouble()), ManaField.cellOf(at.y.toDouble())))

  fun tierOf(cell: Cell): ManaTier = memory[cell]?.tier ?: naturalTierOf(cell)

  /** Moves the clock by [seconds] and settles every cell that holds a change. */
  fun update(rule: ManaTierRule, seconds: Double) {
    now += seconds
    val touched = HashSet<Cell>()

    field.tileKeys().forEach { key ->
      val originX = ManaField.tileXOf(key) * TILE_CELLS
      val originY = ManaField.tileYOf(key) * TILE_CELLS
      for (index in 0 until TILE_CELLS * TILE_CELLS) {
        val cell = Cell(originX + index % TILE_CELLS, originY + index / TILE_CELLS)
        touched += cell
        if (primed) settle(rule, cell) else prime(cell)
      }
    }
    primed = true

    // A cell whose tile faded away is back on its start density, which is a plain cut.
    memory.keys.retainAll(touched)
  }

  /** After a restart the saved field is already where it is: no dwell, and nothing to tell anyone. */
  private fun prime(cell: Cell) {
    val tier = ManaTier.of(densityOf(cell))
    if (tier != naturalTierOf(cell)) memory[cell] = Memory(tier, null, now)
  }

  private fun settle(rule: ManaTierRule, cell: Cell) {
    val known = memory[cell]
    val current = known?.tier ?: naturalTierOf(cell)
    val wanted = rule.tierFor(densityOf(cell), current)

    if (wanted == current) {
      if (known != null) {
        known.pending = null
        if (current == naturalTierOf(cell)) memory.remove(cell)
      }
      return
    }

    val state = known ?: Memory(current, null, now).also { memory[cell] = it }
    if (state.pending != wanted) {
      state.pending = wanted
      state.pendingSince = now
    }

    if (now - state.pendingSince >= rule.dwellSeconds) {
      state.tier = wanted
      state.pending = null
      listeners.forEach { it.onTierChanged(cell, current, wanted) }
    }
  }

  /** The tier of the start density, which is where every cell without memory sits. */
  private fun naturalTierOf(cell: Cell): ManaTier = ManaTier.of(ground.startDensity(cell))

  private fun densityOf(cell: Cell): Double = (ground.startDensity(cell) + field.deltaOf(cell.x, cell.y)).coerceAtLeast(0.0)
}

/** When a cell may change tier. */
class ManaTierRule(val dwellSeconds: Double) {

  /** The tier a cell at [density] moves to from [current]: up at the border, down only well below it. */
  fun tierFor(density: Double, current: ManaTier): ManaTier {
    val plain = ManaTier.of(density)
    if (plain >= current) return plain

    return ManaTier.entries.last { it <= current && density >= it.from - HYSTERESIS }
  }

  companion object {
    const val HYSTERESIS = 0.03
  }
}
