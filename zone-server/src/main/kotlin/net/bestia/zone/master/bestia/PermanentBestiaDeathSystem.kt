package net.bestia.zone.master.bestia

import net.bestia.zone.master.ecs.DeathWearSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import org.springframework.stereotype.Component as SpringComponent

/**
 * Loses an owned bestia for good when its killing blow overkilled it.
 *
 * After every other death system, so its bag, its mail sacks and its gear wear are settled while the body is still there.
 */
@SpringComponent
class PermanentBestiaDeathSystem(
  private val loss: PlayerBestiaLoss,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(DeathWearSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class, OwnedBestia::class, Account::class)
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, OwnedBestia::class).each { id ->
      if (get<Dead>().lostForGood) {
        loss.lose(world, id)
      }
    }
  }
}
