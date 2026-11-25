package net.bestia.zone.battle

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.Random

/**
 * Refuses a master's command to a bestia far above their level. Every command may set the bestia off: never up to
 * [FREE_LEVEL_GAP] levels above its master, always from [FULL_LEVEL_GAP] on, and evenly more likely in between.
 * Set off, it turns [StatusEffectId.UNRULY] for a minute and ignores every command while its own AI drives it.
 *
 * There is no level gate on owning a bestia; this is what a master pays for one they cannot yet handle.
 */
@Service
class UnrulyGuard(
  private val statusEffects: StatusEffectService,
  private val masters: MasterEntityResolver,
  private val out: OutMessageProcessor,
  private val random: Random = ThreadLocalRandomSource,
) {

  /**
   * Call on the tick, for a command [accountId] gave [entityId]. Only the moment it turns unruly is told; the
   * client shows the debuff, so the commands refused after that need no answer.
   */
  fun refuses(world: World, accountId: AccountId, entityId: EntityId): Boolean {
    if (!world.has(entityId, OwnedBestia::class)) {
      return false
    }
    if (isUnruly(world, entityId)) {
      return true
    }

    val gap = levelGap(world, entityId) ?: return false
    if (random.nextDouble() >= chanceAt(gap)) {
      return false
    }

    statusEffects.applyEffect(world, entityId, StatusEffectId.UNRULY.id, level = 1)
    out.sendToPlayer(accountId, OperationErrorSMSG(OpError.BESTIA_UNRULY))
    return true
  }

  /** How many levels the bestia stands above its master, or null while its master is not in the world. */
  private fun levelGap(world: World, entityId: EntityId): Int? {
    val masterId = masters.masterEntityOf(world, entityId) ?: return null
    val masterLevel = world.get(masterId, Level::class)?.level ?: return null
    val bestiaLevel = world.get(entityId, Level::class)?.level ?: return null

    return bestiaLevel - masterLevel
  }

  companion object {
    const val FREE_LEVEL_GAP = 20
    const val FULL_LEVEL_GAP = 40

    fun isUnruly(world: World, entityId: EntityId): Boolean {
      return world.get(entityId, StatusEffects::class)?.hasEffect(StatusEffectId.UNRULY.id) ?: false
    }

    /** The chance one command sets a bestia [gap] levels above its master off, from 0 to 1. */
    fun chanceAt(gap: Int): Double {
      return ((gap - FREE_LEVEL_GAP).toDouble() / (FULL_LEVEL_GAP - FREE_LEVEL_GAP)).coerceIn(0.0, 1.0)
    }
  }
}
