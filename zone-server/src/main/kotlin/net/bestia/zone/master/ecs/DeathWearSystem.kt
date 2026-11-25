package net.bestia.zone.master.ecs

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.casting.GearOperations
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.honor.PlayerKillHonorSystem
import net.bestia.zone.postal.courier.MailSackDropSystem
import net.bestia.zone.spawn.ecs.BestiaBagDropSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Wears down the gear of a player-owned body once per death.
 *
 * Last in the death phase: a piece that breaks comes off into the bag, and it must not fall out with the bag or a
 * mail sack.
 */
@SpringComponent
class DeathWearSystem(
  private val rules: WorldRulesConfig,
  private val gear: GearOperations,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(BestiaBagDropSystem::class, MailSackDropSystem::class, PlayerKillHonorSystem::class)

  override val reads: ComponentClassSet = setOf(Account::class, Master::class, OwnedBestia::class, StatusEffects::class)
  override val writes: ComponentClassSet = setOf(Dead::class, Equipment::class, Inventory::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Account::class, Equipment::class).each { id ->
      val dead = get<Dead>()
      if (dead.gearWorn) {
        return@each
      }
      dead.gearWorn = true

      gear.wear(world, id, rules.deathWearPercent, GearOperations.ALL_BUT_LIGHT)
    }
  }
}
