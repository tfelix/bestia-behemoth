package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.ecs.BestiaBagDropSystem
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Settles a player's death once: charges whoever killed a player or a player's bestia, and rewards whoever killed
 * an aggressor in somebody else's defense, an outlaw or a thief. Defense of oneself is free and earns nothing.
 *
 * A victim pays out as an outlaw or thief only once a day to the same killer, so a master gains nothing from
 * killing a second character of their own over and over.
 */
@Component
class PlayerKillHonorSystem(
  private val honor: HonorService,
  private val masters: MasterEntityResolver,
  private val aggression: Aggression,
  private val allegiance: OrderAllegiance,
  private val clock: Clock = Clock.systemUTC(),
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(PlayerDeathSystem::class, BestiaBagDropSystem::class)

  override val reads: ComponentClassSet =
    setOf(Account::class, TakenDamage::class, Level::class, OwnedBestia::class) + HonorService.READS
  override val writes: ComponentClassSet = setOf(Dead::class) + HonorService.WRITES

  /** When each killer was last paid for each victim, by master id. Lost on restart, which costs one extra bounty. */
  private val bountiesPaid = HashMap<Pair<Long, Long>, Instant>()

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, Account::class).each { victim ->
      val dead = get<Dead>()
      if (dead.honorSettled) {
        return@each
      }
      dead.honorSettled = true

      val victimAccount = get<Account>().accountId
      val killerAccount = world.get(victim, TakenDamage::class)?.topAccount(world) ?: return@each
      if (killerAccount == victimAccount) {
        return@each
      }

      val killer = masters.selectedMasterEntityOf(killerAccount) ?: return@each
      val at = world.get(killer, Position::class)?.toVec3L() ?: return@each

      if (aggression.isAggressor(world, victim)) {
        rewardDefense(world, killer, victim, at)
      } else if (isMarked(world, victim)) {
        rewardBounty(world, killer, victim, at)
      } else {
        actFor(world, killer, victim)?.let { honor.record(world, it, killer, at) }
      }
    }
  }

  /** Only for somebody else: fighting back for oneself is free, and earns nothing. */
  private fun rewardDefense(world: World, killer: EntityId, aggressor: EntityId, at: Vec3L) {
    val defended = aggression.victimOf(world, aggressor) ?: return
    val defendedAccount = world.get(defended, Account::class)?.accountId
    if (defendedAccount == world.get(killer, Account::class)?.accountId) {
      return
    }

    honor.record(world, HonorAct.DEFEND_FROM_AGGRESSOR, killer, at)
  }

  private fun rewardBounty(world: World, killer: EntityId, victim: EntityId, at: Vec3L) {
    val killerId = world.get(killer, Master::class)?.masterId ?: return
    val victimId = world.get(victim, Master::class)?.masterId ?: return
    val now = clock.instant()
    val lastPaid = bountiesPaid[killerId to victimId]
    if (lastPaid != null && lastPaid.plus(BOUNTY_INTERVAL).isAfter(now)) {
      return
    }
    bountiesPaid[killerId to victimId] = now

    val owed = -(world.get(victim, Honor::class)?.shownValue ?: 0)
    if (owed > 0) {
      honor.record(world, HonorAct.KILL_OUTLAW, killer, at, maxGain = owed)
    }
    if (isThief(world, victim)) {
      honor.record(world, HonorAct.KILL_THIEF, killer, at)
    }
  }

  /** Outlaws and thieves: masters, not their bestias, and nobody else pays out. */
  private fun isMarked(world: World, victim: EntityId): Boolean {
    val shown = world.get(victim, Honor::class)?.shownValue ?: return false

    return shown < 0 || isThief(world, victim)
  }

  private fun isThief(world: World, victim: EntityId): Boolean {
    return world.get(victim, StatusEffects::class)?.hasEffect(StatusEffectId.THIEF.id) == true
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

    val BOUNTY_INTERVAL: Duration = Duration.ofDays(1)
  }
}
