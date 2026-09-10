package net.bestia.zone.economy

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Lets the settlements a player has disturbed decay back, and deletes their rows when they have.
 *
 * ### It reads and writes nothing
 *
 * Deliberately, and it is why this can sit in the schedule for free: the economy is not made of
 * components. Every settlement a player is *near* is caught up by whoever asks about it, which is the
 * same answer by I14 - so all that is left for a system to do is sweep the towns nobody is asking about,
 * which is a walk over a map of the handful that are away from their reference.
 *
 * Without it the table would only ever grow: a village a player wrecked and walked away from would keep
 * its row until somebody happened to visit again.
 */
@Component
@Order(87)
class SettlementEconomySystem(
  private val economy: SettlementEconomyService,
) : System {

  /**
   * Rare on purpose. The books move in game-days and the shortest of those is twenty minutes of real
   * time, so anything faster is arithmetic nobody could observe.
   */
  override val schedule: Schedule = Schedule.EverySeconds(30f)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    economy.catchUpAll()
  }
}
