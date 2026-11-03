package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.ecs.BestiaBagDropSystem
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Charges the master who killed a player, or a player's bestia, once per death. Defense is free. */
@Component
class PlayerKillHonorSystem(
  private val honor: HonorService,
  private val masters: MasterEntityResolver,
  private val aggression: Aggression,
  private val allegiance: OrderAllegiance,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(PlayerDeathSystem::class, BestiaBagDropSystem::class)

  override val reads: ComponentClassSet =
    setOf(Account::class, TakenDamage::class, Level::class, OwnedBestia::class) + HonorService.READS
  override val writes: ComponentClassSet = setOf(Dead::class) + HonorService.WRITES

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Account::class).each { victim ->
      val dead = get<Dead>()
      if (dead.honorSettled) {
        return@each
      }
      dead.honorSettled = true

      val victimAccount = get<Account>().accountId
      val killerAccount = world.get(victim, TakenDamage::class)?.topAccount(world) ?: return@each
      if (killerAccount == victimAccount || aggression.isAggressor(world, victim)) {
        return@each
      }

      val killer = masters.selectedMasterEntityOf(killerAccount) ?: return@each
      val act = actFor(world, killer, victim) ?: return@each
      val at = world.get(killer, Position::class)?.toVec3L() ?: return@each

      honor.record(world, act, killer, at)
    }
  }

  /** Null for an even fight between rival Orders, which is what the Orders are for. */
  private fun actFor(world: World, killer: EntityId, victim: EntityId): HonorAct? {
    if (!world.has(victim, Master::class)) {
      return HonorAct.KILL_PLAYER_BESTIA
    }
    if (!allegiance.areOpposing(world, killer, victim)) {
      return HonorAct.KILL_PLAYER
    }

    val gap = levelOf(world, killer) - levelOf(world, victim)

    return HonorAct.KILL_WEAK_OPPOSING_PLAYER.takeIf { gap >= WEAK_VICTIM_LEVEL_GAP }
  }

  private fun levelOf(world: World, entity: EntityId): Int {
    return world.get(entity, Level::class)?.level ?: 1
  }

  private companion object {
    const val WEAK_VICTIM_LEVEL_GAP = 10
  }
}
