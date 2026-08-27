package net.bestia.zone.ecs.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.floor

/**
 * Charges a player-owned entity for its own death, once, and leaves the body lying there.
 *
 * `@Order(68)`: after `ReceivedDamageSystem` (@50) has added [Dead], and before [DeathSystem] (@70),
 * which skips player-owned entities entirely. That ordering is only honoured because both systems
 * declare [Exp] as written and therefore conflict - `SystemScheduler` places non-conflicting systems
 * in the same wave, where `@Order` buys nothing.
 *
 * [TakenDamage] and `InCombat` are deliberately left alone: the damage ledger stays readable while
 * the body is on the ground, and clearing it belongs to the respawn.
 */
@SpringComponent
@Order(68)
class PlayerDeathSystem(
  private val zoneConfig: ZoneConfig,
) : System {

  override val reads: ComponentClassSet = setOf(Account::class)
  override val writes: ComponentClassSet = setOf(Dead::class, Exp::class, Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Account::class).each { id ->
      val dead = get<Dead>()

      if (dead.resolved) {
        return@each
      }
      dead.resolved = true

      // Whatever it was walking towards, it is not going there.
      world.remove(id, Path::class)

      val exp = world.get(id, Exp::class) ?: return@each
      // Floored, so an almost-empty EXP bar forfeits nothing rather than going negative.
      val lost = floor(exp.value * zoneConfig.deathExpLossFraction).toInt()
      exp.value -= lost

      LOG.debug { "Entity $id died and forfeited $lost EXP, ${exp.value} left" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
