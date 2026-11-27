package net.bestia.zone.doomsday.withering

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import net.bestia.zone.spawn.SpawnCorruption
import net.bestia.zone.world.mana.ManaField
import org.springframework.stereotype.Component

/** Each bestia masters kill on rotten land eases the rot there a little, and with it the Withering's spread. */
@Component
class CorruptedKills(
  private val config: WitheringConfig,
  private val field: CorruptionField,
) : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun onKill(world: World, kill: KillFact) {
    if (kill.creditedMasters.isEmpty()) {
      return
    }
    val x = kill.position.x.toDouble()
    val y = kill.position.y.toDouble()
    if (field.intensityAt(x, y) < SpawnCorruption.BREEDS_CORRUPTED) {
      return
    }
    field.change(ManaField.Cell(ManaField.cellOf(x), ManaField.cellOf(y)), -config.healPerKill)
  }
}
