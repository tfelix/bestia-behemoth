package net.bestia.zone.ecs.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.ecs.battle.attack.AttackTarget
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.world.prop.PlayerStructureDeathSystem
import net.bestia.zone.world.prop.PropDeathDivergenceSystem
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.floor
import net.bestia.zone.entity.ecs.Dead

/**
 * Charges a player-owned entity for its own death, once, and leaves the body lying there.
 *
 * In the death phase, after `ReceivedDamageSystem` has added [Dead], and before [DeathSystem], which skips
 * player-owned entities entirely.
 *
 * [TakenDamage] and `InCombat` are deliberately left alone: the damage ledger stays readable while
 * the body is on the ground, and clearing it belongs to the respawn.
 */
@SpringComponent
class PlayerDeathSystem(
  private val zoneConfig: WorldRulesConfig,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(PropDeathDivergenceSystem::class, PlayerStructureDeathSystem::class)

  override val reads: ComponentClassSet = setOf(Account::class)
  override val writes: ComponentClassSet = setOf(Dead::class, Exp::class, Path::class, AttackTarget::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Account::class).each { id ->
      val dead = get<Dead>()

      if (dead.resolved) {
        return@each
      }
      dead.resolved = true

      // Whatever it was walking towards, it is not going there.
      world.remove(id, Path::class)

      // Nor is it still fighting. AttackSystem gives up on a dead attacker anyway; dropped here too so a
      // revived body does not resume a fight its owner has long stopped watching.
      world.remove(id, AttackTarget::class)

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
