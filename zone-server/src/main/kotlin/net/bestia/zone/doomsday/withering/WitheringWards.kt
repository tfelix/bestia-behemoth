package net.bestia.zone.doomsday.withering

import net.bestia.zone.casting.ritual.RitualObserver
import net.bestia.zone.doomsday.persistence.StoredWardedCell
import net.bestia.zone.doomsday.persistence.StoredWardedCellRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A ritual completed on corrupted land cleanses it: the land around the circle heals at once, and the Withering cannot
 * rot it again for a week. Heard on the tick; the wards are stored by the Withering's next run, off it.
 */
@Component
class WitheringWards(
  private val config: WitheringConfig,
  private val field: CorruptionField,
  private val wards: StoredWardedCellRepository,
  private val clock: Clock = Clock.systemUTC(),
) : RitualObserver {

  private val pending = ConcurrentLinkedQueue<StoredWardedCell>()

  override fun onRitualCompleted(world: World, at: Vec3L, participants: List<EntityId>) {
    val cells = CorruptionSpread.cellsWithin(at.x.toDouble(), at.y.toDouble(), config.cleanseRadiusMetres)
    if (cells.none { field.intensityOf(it) > 0.0 }) {
      return
    }

    val until = today() + config.wardDays
    cells.forEach { cell ->
      field.change(cell, -config.cleanseAmount)
      pending += StoredWardedCell(cell.key, until)
    }
  }

  /** The cells warded right now, by `ManaField.Cell.key`. Off the tick: stores the wards heard since the last ask. */
  fun wardedNow(): Set<Long> {
    val heard = generateSequence { pending.poll() }.toList()
    if (heard.isNotEmpty()) {
      wards.saveAll(heard)
    }
    val now = today()
    return wards.findAll().filter { it.untilDay > now }.mapTo(HashSet()) { it.cellKey }
  }

  private fun today(): Double {
    return clock.millis() / 1000.0 / SECONDS_PER_DAY
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
  }
}
